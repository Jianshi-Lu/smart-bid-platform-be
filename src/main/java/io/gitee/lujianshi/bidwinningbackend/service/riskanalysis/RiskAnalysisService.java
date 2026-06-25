package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.SupplierDetailMapper;
import io.gitee.lujianshi.bidwinningbackend.utils.PromptFiller;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 风险分析业务服务。同步调用 DeepSeek，落库风险分析结果。
 */
@Service
public class RiskAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisService.class);
    private static final String PROMPT_VERSION = "v1";

    private final ChatClient chatClient;

    @Resource
    private BidBackgroundLoader loader;

    @Resource
    private RiskAnalysisMapper mapper;

    @Resource
    private SupplierDetailMapper supplierDetailMapper;

    @Resource
    private PromptFiller promptFiller;

    public RiskAnalysisService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 触发一次风险分析。同步调用 DeepSeek API，解析 JSON 填字段，落库并返全字段实体。
     *
     * @param supplierId 供应商 ID
     * @return 含 analysisId 的完整 RiskAnalysis
     * @throws IllegalArgumentException 供应商不存在
     */
    public RiskAnalysis analyzeRisk(Integer supplierId) {
        log.info(">>> [风险分析] 开始 supplierId={}", supplierId);

        SupplierDetail detail = supplierDetailMapper.selectById(supplierId);
        if (detail == null) {
            log.warn("<<< [风险分析] 供应商不存在 supplierId={}", supplierId);
            throw new IllegalArgumentException("供应商不存在: " + supplierId);
        }

        log.info(">>> [风险分析] 供应商信息 name={} quotePrice={} contact={}",
                detail.getSupplierName(), detail.getQuotePrice(), detail.getSupplierContact());

        String background = loader.loadBackground(supplierId);
        Map<String, String> vars = Map.of(
                "supplierName", detail.getSupplierName() != null ? detail.getSupplierName() : "",
                "quotePrice", detail.getQuotePrice() != null ? detail.getQuotePrice().toString() : "",
                "supplierContact", detail.getSupplierContact() != null ? detail.getSupplierContact() : "",
                "background", background
        );

        String systemPrompt = promptFiller.loadFromResource("prompts/risk-analysis-system-v1.txt");
        String userPrompt = promptFiller.fillFromResource("prompts/risk-analysis-user-v1.txt", vars);

        log.info(">>> [风险分析] 发送 Prompt:\nsystem=\n{}\nuser=\n{}", systemPrompt, userPrompt);

        long startNs = System.nanoTime();
        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        LocalDateTime analyzedAt = LocalDateTime.now();

        log.info("<<< [风险分析] AI 响应 (length={}, elapsed={}ms, analyzedAt={}):\n{}",
                raw != null ? raw.length() : 0, elapsedMs, analyzedAt, raw);

        RiskAnalysis analysis = parseAndBuild(supplierId, detail.getSupplierName(), raw, analyzedAt);

        RiskAnalysis saved = persist(analysis);
        log.info("<<< [风险分析] 落库成功 analysisId={}", saved.getAnalysisId());
        return saved;
    }

    private RiskAnalysis parseAndBuild(Integer supplierId, String supplierName, String raw, LocalDateTime analyzedAt) {
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
        a.setSupplierId(supplierId);
        a.setSupplierName(supplierName);
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
     * 查询指定供应商的历史分析记录（按时间倒序）。
     */
    public List<RiskAnalysis> getHistory(Integer supplierId) {
        return mapper.selectBySupplierId(supplierId);
    }
}
