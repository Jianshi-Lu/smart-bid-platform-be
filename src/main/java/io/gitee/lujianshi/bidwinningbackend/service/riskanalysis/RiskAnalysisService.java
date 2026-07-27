package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import io.gitee.lujianshi.bidwinningbackend.entity.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.CredentialMarkdownAggregationService;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;

import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;

import io.gitee.lujianshi.bidwinningbackend.utils.PromptFiller;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 风险分析业务服务。同步调用 OpenAI 兼容 LM Studio，通过 Structured Output JSON Schema 约束输出，
 * 再解析并落库风险分析结果。
 *
 * <p>写入 {@code risk_analysis} 表时通过 {@link #self} 走 Spring 代理调用 {@link #persist(RiskAnalysis)}，
 * 避免同类自调用导致事务注解失效。</p>
 *
 * @since V18
 */
@Service
public class RiskAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisService.class);
    private static final String PROMPT_VERSION = "v1";

    /**
     * Structured Output JSON Schema，约束 LLM 仅输出符合此结构的 JSON 对象。
     * 通过 {@link OpenAiChatOptions.Builder#responseFormat(ResponseFormat)} 传给下游 OpenAI 兼容服务。
     * 字段定义与 {@link RiskAnalysis} 实体保持一致，riskLevel 限定 4 档枚举。
     */
    private static final String OUTPUT_SCHEMA =
            """
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

    /**
     * 自注入 Spring 代理,用于绕过 {@code @Transactional} 同类自调失效。
     * {@link #persist(RiskAnalysis)} 标了 {@code @Transactional(rollbackFor = Exception.class)},
     * 但 {@code analyzeRisk} 同类内调它时事务代理不会触发,得走 self 走代理。
     * <p>{@code @Lazy} 避免循环依赖(Spring 实例化本 Bean 时尚无代理可用,需延后注入)。</p>
     */
    @Resource
    @Lazy
    private RiskAnalysisService self;

    @Resource
    private BidRecordLoader loader;

    @Resource
    private RiskAnalysisMapper mapper;

    @Resource
    private BidRecordMapper bidRecordMapper;

    @Resource
    private CredentialMarkdownAggregationService markdownAggregationService;

    @Resource
    private FileStorage fileStorage;

    @Resource
    private PromptFiller promptFiller;

    /**
     * 构造风险分析 ChatClient，并把 Structured Output JSON Schema 固化到默认调用参数中。
     *
     * @param openAiChatModel Spring AI OpenAI 兼容聊天模型 Bean
     */
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

        log.info(">>> [风险分析] 投标人信息 name={} quotePrice={} contact={}",
                record.getBidderName(), record.getQuotePrice(), record.getBidderContact());

        String mergedMarkdownPath = markdownAggregationService.aggregate(bidRecordId);
        String mergedMarkdown = readMergedMarkdown(mergedMarkdownPath);
        String detailInfo = loader.loadDetail(bidRecordId);
        Map<String, String> vars = Map.of(
                "bidderName", record.getBidderName() != null ? record.getBidderName() : "",
                "quotePrice", record.getQuotePrice() != null ? record.getQuotePrice().toString() : "",
                "bidderContact", record.getBidderContact() != null ? record.getBidderContact() : "",
                "credentials", mergedMarkdown,
                "detail", detailInfo
        );

        String systemPrompt = promptFiller.loadFromResource("prompts/risk-analysis-system-v1.txt");
        String userPrompt = promptFiller.fillFromResource("prompts/risk-analysis-user-v1.txt", vars);

        log.info(">>> [风险分析] 发送 Prompt:\nsystem=\n{}\nuser=\n{}", systemPrompt, userPrompt);

        long startNs = System.nanoTime();

        // 调用模型,获取完整 ChatResponse(防止内容回复从 content 落入 reasoningContent,见 extractRawContent JavaDoc)
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();
        String raw = extractRawContent(chatResponse);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        LocalDateTime analyzedAt = LocalDateTime.now();

        log.info("<<< [风险分析] AI 响应 (length={}, elapsed={}ms, analyzedAt={}):\n{}",
                raw != null ? raw.length() : 0, elapsedMs, analyzedAt, raw);

        RiskAnalysis analysis = parseAndBuild(bidRecordId, record.getBidderName(), raw, analyzedAt);

        RiskAnalysis saved = self.persist(analysis);
        log.info("<<< [风险分析] 落库成功 analysisId={}", saved.getAnalysisId());
        return saved;
    }

    /** 读取汇总服务写入的投标记录级 Markdown，作为本次唯一的资信材料输入。 */
    private String readMergedMarkdown(String mergedMarkdownPath) {
        try (InputStream input = fileStorage.open(mergedMarkdownPath)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("资信材料汇总文件读取失败", e);
        }
    }

    /**
     * 从 ChatResponse 抽取 LLM 原始文本输出。
     *
     * <p><b>为什么需要这个方法</b>:某些 Hybrid Reasoning(混合推理)模型
     * (实测代表: <code>huihui-qwen3.6-35b-a3b-claude-4.7-opus-abliterated-mlx</code>)
     * 在启用了 {@code response_format=json_schema} 的情况下,仍然把"实际 JSON 输出"写进
     * OpenAI 兼容响应的 <code>reasoning_content</code> 字段,而不是 <code>content</code>
     * 字段。LM Studio 实际日志:</p>
     *
     * <pre>
     * "choices": [{
     *   "message": {
     *     "role": "assistant",
     *     "content": "",                                          // ← 空
     *     "reasoning_content": "{\"riskLevel\": \"MEDIUM\", ...}"  // ← JSON 在这里
     *   },
     *   "finish_reason": "stop"
     * }]
     * </pre>
     *
     * <p>如果仅用 {@code chatClient.call().content()},会读到空字符串 → {@code parseAndBuild}
     * 拿到 null → DB 写入撞 <code>risk_level</code> NOT NULL 约束,产生 500 内部服务错误。</p>
     *
     * <p><b>为什么取 {@code AssistantMessage.metadata["reasoningContent"]} 能 work</b>:
     * 反编译 <code>spring-ai-openai-2.0.0.jar</code> 确认 {@code OpenAiChatModel}
     * 内部已经实现了这个透传:</p>
     *
     * <ol>
     *   <li>{@code OpenAiChatModel.getReasoningContent(Choice)} 私有方法从
     *       {@code ChatCompletionMessage._additionalProperties().get("reasoning_content")}
     *       读取(OpenAI 官方字段不在 POJO schema 中,只有 additionalProperties 能兜底)</li>
     *   <li>{@code OpenAiChatModel.internalCall} 把读到的 reasoning 字符串塞进 7 键 Map,
     *       交给 {@code buildGeneration} 构造 {@code AssistantMessage},作为 metadata 一员</li>
     *   <li>map key 是常量 {@code private static final String REASONING_CONTENT = "reasoningContent"};
     *       (注意:snake_case 的 JSON 字段在 Spring AI 透传时被改写成 camelCase)</li>
     * </ol>
     *
     * <p>所以 <b>不需要</b>降级到 raw HTTP 调用、也不需要绕开 Spring AI,
     * 只需要从 {@code ChatResponse} 层级拿 {@code metadata} 即可。</p>
     *
     * <p>降级路径触发时打 INFO 日志(<i>"降级到 reasoningContent"</i>),便于排查"为什么我 prompt 没变,
     * JSON 却在 reasoning 字段里"的偶发问题 — 一般表明当前模型版本不遵守 Structured Output。</p>
     *
     * @param response Spring AI {@link ChatClient#call()} 返回的 {@link ChatResponse},可为 {@code null}
     * @return 优先 {@code AssistantMessage.getContent()},为空时降级
     *         {@code metadata["reasoningContent"]},全空返 {@code ""}
     */
    private String extractRawContent(ChatResponse response) {
        if (response == null) {
            return "";
        }
        Generation gen = response.getResult();
        if (gen == null) {
            return "";
        }
        AssistantMessage msg = gen.getOutput();
        // 取 content 字段
        String content = msg.getText();
        if (content != null && !content.isBlank()) {
            return content;
        }
        // 兜底:Hybrid Reasoning 模型把 JSON 输出塞进 reasoning_content。
        // 取 AssistantMessage.metadata 是因为 spring-ai-openai 2.0 内 OpenAiChatModel
        // 已把 reasoning_content 字段透传到这里,key="reasoningContent"(camelCase)。
        Map<String, Object> metadata = msg.getMetadata();
        Object reasoning = metadata.get("reasoningContent");
        // 取 reasoning_content 字段
        if (reasoning != null && !reasoning.toString().isBlank()) {
            log.info("[风险分析] content 为空,降级到 reasoningContent (length={})",
                    reasoning.toString().length());
            return reasoning.toString();
        }
        return "";
    }

    /**
     * 解析 AI 原始 JSON 响应，构造 RiskAnalysis 实体。
     * <p>JSON 解析失败时 riskLevel/summary/suggestions 字段保持 null，rawResponse 始终保留。</p>
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
     * 落库风险分析记录。analysisId 通过 useGeneratedKeys 回填。
     *
     * @param analysis 待落库的风险分析实体
     * @return 已落库实体（含回填后的 analysisId）
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskAnalysis persist(RiskAnalysis analysis) {
        mapper.insert(analysis);
        return analysis;
    }

    /**
     * 查询指定投标记录的历史分析记录（按时间倒序）。
     *
     * @param bidRecordId 投标记录 ID
     * @return 历史分析记录列表，无记录时返回空列表
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
