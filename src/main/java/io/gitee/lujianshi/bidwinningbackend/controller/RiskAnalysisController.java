package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.service.riskanalysis.RiskAnalysisService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风险分析记录查询 Controller。
 *
 * <p>本 Controller 仅为敏感风险数据提供前端只读入口：不暴露 DELETE / PATCH / POST 写端点，
 * 全部新增与删除走数据库手工操作，AI 触发分析走
 * {@code BidRecordController.analyzeRisk}（副作用为 {@link RiskAnalysisService#persist}）。</p>
 *
 * <p>当前 2 端点：</p>
 * <ul>
 *   <li>{@code GET /risk-analysis} — 分页查询全部历史分析记录</li>
 *   <li>{@code GET /risk-analysis/by-bid-record/{id}} — 按投标记录 ID 查询其所有历史分析记录</li>
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
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误: " + e.getMessage());
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
}
