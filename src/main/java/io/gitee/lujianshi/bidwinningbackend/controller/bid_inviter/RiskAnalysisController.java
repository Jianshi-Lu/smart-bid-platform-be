package io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.service.riskanalysis.RiskAnalysisService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风险分析记录查询 Controller。
 *
 * <p>本 Controller 统一承载风险分析的查询与触发入口。资信文件上传和转换仍由
 * {@code credentialfile} 模块对外暴露，风险分析入口负责完整编排“状态校验—Markdown 汇总—
 * 大模型分析—结果落库”流程。</p>
 *
 * <p>当前 3 端点：</p>
 * <ul>
 *   <li>{@code GET /risk-analysis} — 分页查询全部历史分析记录</li>
 *   <li>{@code GET /risk-analysis/by-bid-record/{id}} — 按投标记录 ID 查询其所有历史分析记录</li>
 *   <li>{@code POST /bid-record/analyze-risk/{id}} — 触发一次整体资信材料风险分析</li>
 * </ul>
 */
@RestController
public class RiskAnalysisController {

    @Resource
    private RiskAnalysisService service;

    /**
     * 分页查询全部历史风险分析记录。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort     排序字段（默认 {@code analysis_id desc}）
     * @return 分页结果（PageHelper ThreadLocal 模式，finally 确保清理）
     */
    @GetMapping("/risk-analysis")
    public Result<PageInfo<RiskAnalysis>> listRiskAnalysis(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "analysis_id desc") String sort) {
        try {
            PageHelper.startPage(page, pageSize, sort);
            List<RiskAnalysis> list = service.listAll();
            return Result.success(new PageInfo<>(list), "查询成功");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 按投标记录 ID 查询其所有历史风险分析记录（按 {@code analyzedAt} 倒序）。
     *
     * @param id 投标记录 ID
     * @return 历史分析记录列表，无记录时返空列表
     */
    @GetMapping("/risk-analysis/by-bid-record/{id}")
    public Result<List<RiskAnalysis>> getHistoryByBidRecord(@PathVariable Integer id) {
        return Result.success(service.getHistory(id), "查询成功");
    }

    /**
     * 触发一次整体资信材料风险分析。
     *
     * <p>服务端会先校验所有资信批次和文件均已转换完成，再按批次和文件顺序汇总 Markdown，
     * 最后将完整材料一次性提交给大模型。</p>
     *
     * @param id 投标记录 ID
     * @return 含 analysisId 的完整 RiskAnalysis
     */
    @PostMapping("/bid-record/analyze-risk/{id}")
    public Result<RiskAnalysis> analyzeRisk(@PathVariable Integer id) {
        return Result.success(service.analyzeRisk(id), "分析完成");
    }
}
