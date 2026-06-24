package io.gitee.lujianshi.bidwinningbackend.service.riskanalysis;

import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.mapper.RiskAnalysisMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 风险分析业务服务。
 *
 * <p>提供供应商的 AI 风险分析能力：</p>
 * <ul>
 *   <li>{@link #analyzeRisk(Integer)} — 触发一次风险分析（异步，逻辑待实现）</li>
 *   <li>{@link #getHistory(Integer)} — 查询指定供应商的历史分析记录</li>
 * </ul>
 *
 * <h3>依赖组件</h3>
 * <ul>
 *   <li>{@code ChatClient} — Spring AI DeepSeek 聊天客户端（TODO：逻辑实现时配置）</li>
 *   <li>{@link BidBackgroundLoader} — 供应商背景加载器（默认 DB 实现）</li>
 *   <li>{@link RiskAnalysisMapper} — 风险分析结果 Mapper</li>
 *   <li>{@link ResourceLoader} — Spring 资源加载器（加载 prompt 模板）</li>
 * </ul>
 *
 * @see BidBackgroundLoader
 * @see RiskAnalysisMapper
 * @see PromptFiller
 */
@Service
public class RiskAnalysisService {

    @Resource
    private BidBackgroundLoader loader;

    @Resource
    private RiskAnalysisMapper mapper;

    // TODO: Spring AI 配置完成后启用
    // @Autowired(required = false)
    // private ChatClient chatClient;

    @Autowired(required = false)
    private ResourceLoader resourceLoader;

    /**
     * 触发一次风险分析。
     *
     * <p>当前版本直接返回 {@code null}，逻辑待实现。</p>
     *
     * @param supplierId 供应商 ID
     * @return 风险分析结果（当前返回 null）
     */
    public RiskAnalysis analyzeRisk(Integer supplierId) {
        // TODO: 1. 校验供应商存在
        // TODO: 2. loader.loadBackground(supplierId) 拼装背景文本
        // TODO: 3. 加载 system + user prompt 模板
        // TODO: 4. PromptFiller.fill() 填充变量
        // TODO: 5. chatClient.prompt().system().user().call().content()
        // TODO: 6. 解析模型 JSON 响应
        // TODO: 7. mapper.insert() 落库
        // TODO: 8. 返回 RiskAnalysis
        return null;
    }

    /**
     * 查询指定供应商的历史分析记录。
     *
     * @param supplierId 供应商 ID
     * @return 历史分析记录列表（按分析时间倒序），无记录时返回空列表
     */
    public List<RiskAnalysis> getHistory(Integer supplierId) {
        // TODO: 调用 mapper.selectBySupplierId(supplierId)
        return List.of();
    }
}
