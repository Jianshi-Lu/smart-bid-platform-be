package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.tendererdetail.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tendererdetail.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tendererdetail.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.entity.TendererDetail;
import io.gitee.lujianshi.bidwinningbackend.service.TendererDetailService;
import io.gitee.lujianshi.bidwinningbackend.service.riskanalysis.RiskAnalysisService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投标人明细 Controller。8 端点（6 标准 CRUD + 2 AI 风险分析）。
 */
@RestController
public class TendererDetailController {

    @Resource
    private TendererDetailService service;

    @Resource
    private RiskAnalysisService riskAnalysisService;

    /**
     * 创建投标人明细。
     *
     * @param request 创建请求（tendererName / quotePrice / tendererContact 必填，credentials 可选）
     * @return 创建的实体（含自增 tendererId）
     */
    @PostMapping("/add-tenderer-detail")
    public Result<TendererDetail> addTendererDetail(@Valid @RequestBody AddRequest request) {
        TendererDetail entity = new TendererDetail();
        entity.setTendererName(request.getTendererName());
        entity.setQuotePrice(request.getQuotePrice());
        entity.setTendererContact(request.getTendererContact());
        entity.setCredentials(request.getCredentials());
        service.add(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * 按 ID 查询投标人明细。
     *
     * @param id 投标人 ID
     * @return 实体（200）或 null（404）
     */
    @GetMapping("/tenderer-detail/{id}")
    public Result<TendererDetail> getTendererDetail(@PathVariable Integer id) {
        TendererDetail entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 部分更新投标人明细（PATCH 语义，null 字段不修改）。
     *
     * @param id      投标人 ID
     * @param request 更新请求（4 字段全可选）
     * @return Diff（更新前后对比）
     */
    @PatchMapping("/tenderer-detail/{id}")
    public Result<Diff<TendererDetail>> patchTendererDetail(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        TendererDetail before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        TendererDetail after = new TendererDetail();
        after.setTendererId(id);
        after.setTendererName(request.getTendererName());
        after.setQuotePrice(request.getQuotePrice());
        after.setTendererContact(request.getTendererContact());
        after.setCredentials(request.getCredentials());
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按 ID 删除投标人明细。
     *
     * @param id 投标人 ID
     * @return 200（成功）或 404（不存在）
     */
    @DeleteMapping("/tenderer-detail/{id}")
    public Result<Void> deleteTendererDetail(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 分页查询投标人明细列表。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort     排序字段（默认 tenderer_id desc）
     * @return 分页结果（PageHelper ThreadLocal 模式，finally 确保清理）
     */
    @GetMapping("/tenderer-detail")
    public Result<PageInfo<TendererDetail>> listTendererDetail(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "tenderer_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<TendererDetail> list = service.listAll();
            PageInfo<TendererDetail> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 批量删除投标人明细。永远返回 200，data 为实际删除的实体列表。
     *
     * @param request 批量删除请求（ids 最多 500 条）
     * @return 实际删除的实体列表
     */
    @PostMapping("/tenderer-detail/batch-delete")
    public Result<List<TendererDetail>> batchDeleteTendererDetail(
            @Valid @RequestBody BatchDeleteRequest request) {
        List<TendererDetail> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /**
     * 触发 AI 风险分析。同步调用 DeepSeek，解析 JSON 后落库。
     *
     * @param id 投标人 ID
     * @return 含 analysisId 的完整 RiskAnalysis
     */
    @PostMapping("/tenderer-detail/analyze-risk/{id}")
    public Result<RiskAnalysis> analyzeRisk(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.analyzeRisk(id), "分析完成");
    }

    /**
     * 查询指定投标人的历史风险分析记录（按时间倒序）。
     *
     * @param id 投标人 ID
     * @return 历史分析记录列表，无记录时返空列表
     */
    @GetMapping("/tenderer-detail/risk-history/{id}")
    public Result<List<RiskAnalysis>> getRiskHistory(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.getHistory(id), "查询成功");
    }
}
