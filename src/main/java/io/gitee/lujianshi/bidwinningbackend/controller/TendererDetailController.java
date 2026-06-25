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

    @GetMapping("/tenderer-detail/{id}")
    public Result<TendererDetail> getTendererDetail(@PathVariable Integer id) {
        TendererDetail entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

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

    @DeleteMapping("/tenderer-detail/{id}")
    public Result<Void> deleteTendererDetail(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

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

    @PostMapping("/tenderer-detail/batch-delete")
    public Result<List<TendererDetail>> batchDeleteTendererDetail(
            @Valid @RequestBody BatchDeleteRequest request) {
        List<TendererDetail> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    @PostMapping("/tenderer-detail/analyze-risk/{id}")
    public Result<RiskAnalysis> analyzeRisk(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.analyzeRisk(id), "分析完成");
    }

    @GetMapping("/tenderer-detail/risk-history/{id}")
    public Result<List<RiskAnalysis>> getRiskHistory(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.getHistory(id), "查询成功");
    }
}
