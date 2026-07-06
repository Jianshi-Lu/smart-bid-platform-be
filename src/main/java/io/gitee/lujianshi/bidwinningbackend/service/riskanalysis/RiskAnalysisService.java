package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import io.gitee.lujianshi.bidwinningbackend.entity.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;

import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;

import io.gitee.lujianshi.bidwinningbackend.utils.PromptFiller;

import jakarta.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 风险分析业务服务。同步调用 OpenAI 兼容 LM Studio（本地小模型），通过 Structured Output JSON Schema 约束输出，落库风险分析结果（V18 字段引用更新：tendererId → bidRecordId；tendererName → bidderName）。
 */
@Service
public class RiskAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisService.class);
    private static final String PROMPT_VERSION = "v1";

    /**
     * Structured Output JSON Schema,约束 LLM 仅输出符合此结构的 JSON 对象。
     * 通过 {@link OpenAiChatOptions#responseFormat} 传给下游 OpenAI 兼容服务(LM Studio 本地)。
     * 字段定义与 {@link RiskAnalysis} 实体保持一致,riskLevel 限定 4 档枚举。
     */
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "riskLevel":   {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"]},
                "summary":     {"type": "string"},
                "suggestions": {"type": "string"}
              },
              "required": ["riskLevel", "summary", "suggestions"],
              "additionalProperties": false
            }
            """;

    private final ChatClient chatClient;

    @Resource
    private BidRecordLoader loader;

    @Resource
    private RiskAnalysisMapper mapper;

    @Resource
    private BidRecordMapper bidRecordMapper;

    @Resource
    private PromptFiller promptFiller;

    // 限定输出格式并事实上创建chatClient(SpringAI)实例
    public RiskAnalysisService(OpenAiChatModel openAiChatModel) {
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(OUTPUT_SCHEMA)
                .build();
        OpenAiChatOptions.Builder defaults = OpenAiChatOptions.builder()
                .responseFormat(responseFormat);
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultOptions(defaults)
                .build();
    }

    /**
     * 触发一次风险分析。同步调用 OpenAI 兼容 LM Studio API，Structured Output 约束 schema 后 JSON 解析填字段，落库并返全字段实体。
     *
     * @param bidRecordId 投标记录 ID
     * @return 含 analysisId 的完整 RiskAnalysis
     * @throws IllegalArgumentException 投标记录不存在
     */
    public RiskAnalysis analyzeRisk(Integer bidRecordId) {
        log.info(">>> [风险分析] 开始 bidRecordId={}", bidRecordId);

        BidRecord record = bidRecordMapper.selectById(bidRecordId);
        if (record == null) {
            log.warn("<<< [风险分析] 投标记录不存在 bidRecordId={}", bidRecordId);
            throw new IllegalArgumentException("投标记录不存在: " + bidRecordId);
        }

        log.info(">>> [风险分析] 投标人信息 name={} quotePrice={} contact={} credentials={}",
                record.getBidderName(), record.getQuotePrice(), record.getBidderContact(), record.getCredentials());

        String detailInfo = loader.loadDetail(bidRecordId);
        Map<String, String> vars = Map.of(
                "bidderName", record.getBidderName() != null ? record.getBidderName() : "",
                "quotePrice", record.getQuotePrice() != null ? record.getQuotePrice().toString() : "",
                "bidderContact", record.getBidderContact() != null ? record.getBidderContact() : "",
                "credentials", record.getCredentials() != null ? record.getCredentials() : "未提供",
                "detail", detailInfo
        );

        String systemPrompt = promptFiller.loadFromResource("prompts/risk-analysis-system-v1.txt");
        String userPrompt = promptFiller.fillFromResource("prompts/risk-analysis-user-v1.txt", vars);

        log.info(">>> [风险分析] 发送 Prompt:\nsystem=\n{}\nuser=\n{}", systemPrompt, userPrompt);

        long startNs = System.nanoTime();

        // 调用模型，获取原始输出
        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        LocalDateTime analyzedAt = LocalDateTime.now();

        log.info("<<< [风险分析] AI 响应 (length={}, elapsed={}ms, analyzedAt={}):\n{}",
                raw != null ? raw.length() : 0, elapsedMs, analyzedAt, raw);

        RiskAnalysis analysis = parseAndBuild(bidRecordId, record.getBidderName(), raw, analyzedAt);

        RiskAnalysis saved = persist(analysis);
        log.info("<<< [风险分析] 落库成功 analysisId={}", saved.getAnalysisId());
        return saved;
    }

    /**
     * 解析 AI 原始 JSON 响应，构造 RiskAnalysis 实体。
     * <p>JSON 解析失败时 riskLevel/summary/suggestions 保持 null，rawResponse 始终保留。</p>
     *
     * @param bidRecordId 投标记录 ID
     * @param bidderName  投标人名称
     * @param raw         AI 原始响应文本
     * @param analyzedAt  分析时间
     * @return RiskAnalysis 实体（未落库）
     */
    private RiskAnalysis parseAndBuild(Integer bidRecordId, String bidderName, String raw, LocalDateTime analyzedAt) {
        String riskLevel = null;
        String summary = null;
        String suggestions = null;

        if (raw != null && !raw.isBlank()) {
            try {
                JSONObject obj = JSON.parseObject(raw);
                riskLevel = obj.getString("riskLevel");
                summary = obj.getString("summary");
                suggestions = obj.getString("suggestions");
                log.info(">>> [风险分析] JSON 解析成功 riskLevel={}", riskLevel);
            } catch (JSONException e) {
                log.warn("[风险分析] AI 响应 JSON 解析失败: {}", e.getMessage());
            }
        }

        RiskAnalysis a = new RiskAnalysis();
        a.setBidRecordId(bidRecordId);
        a.setBidderName(bidderName);
        a.setRiskLevel(riskLevel);
        a.setSummary(summary);
        a.setSuggestions(suggestions);
        a.setRawResponse(raw);
        a.setPromptVersion(PROMPT_VERSION);
        a.setAnalyzedAt(analyzedAt);
        return a;
    }

    /**
     * 落库（独立事务）。analysisId 通过 useGeneratedKeys 回填。
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskAnalysis persist(RiskAnalysis analysis) {
        mapper.insert(analysis);
        return analysis;
    }

    /**
     * 查询指定投标记录的历史分析记录（按时间倒序）。
     */
    public List<RiskAnalysis> getHistory(Integer bidRecordId) {
        return mapper.selectByBidRecordId(bidRecordId);
    }

    /**
     * 查询全部风险分析记录。配合 PageHelper 使用（ThreadLocal 注入分页参数）。
     *
     * @return 全部记录列表
     */
    public List<RiskAnalysis> listAll() {
        return mapper.selectAll();
    }
}
