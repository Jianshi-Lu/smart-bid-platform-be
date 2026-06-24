package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.SupplierDetailMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RiskAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisService.class);

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

        String systemPrompt = promptFiller.fillFromResource("prompts/risk-analysis-system-v1.txt", vars);
        String userPrompt = promptFiller.fillFromResource("prompts/risk-analysis-user-v1.txt", vars);

        log.info(">>> [风险分析] 发送 Prompt:\nsystem=\n{}\nuser=\n{}", systemPrompt, userPrompt);

        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        log.info("<<< [风险分析] AI 响应 (length={}):\n{}", raw != null ? raw.length() : 0, raw);

        RiskAnalysis a = new RiskAnalysis();
        a.setRawResponse(raw);
        return a;
    }

    public List<RiskAnalysis> getHistory(Integer supplierId) {
        return List.of();
    }
}
