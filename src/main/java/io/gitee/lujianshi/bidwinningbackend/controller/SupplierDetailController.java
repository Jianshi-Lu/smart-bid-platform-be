package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.supplierdetail.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.RiskAnalysis;
import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.service.SupplierDetailService;
import io.gitee.lujianshi.bidwinningbackend.service.riskanalysis.RiskAnalysisService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商明细 Controller。8 端点（6 标准 CRUD + 2 AI 风险分析）。
 * 端点详情 / curl 示例见 {@code docs/api.md}。
 */
@RestController
public class SupplierDetailController {

    @Resource
    private SupplierDetailService service;

    @Resource
    private RiskAnalysisService riskAnalysisService;

    @PostMapping("/add-supplier-detail")
    public Result<SupplierDetail> addSupplierDetail(@Valid @RequestBody AddRequest request) {
        SupplierDetail entity = new SupplierDetail();
        entity.setSupplierName(request.getSupplierName());
        entity.setQuotePrice(request.getQuotePrice());
        entity.setSupplierContact(request.getSupplierContact());
        service.add(entity);
        return Result.success(entity, "创建成功");
    }

    @GetMapping("/supplier-detail/{id}")
    public Result<SupplierDetail> getSupplierDetail(@PathVariable Integer id) {
        SupplierDetail entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    @PatchMapping("/supplier-detail/{id}")
    public Result<Diff<SupplierDetail>> patchSupplierDetail(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        SupplierDetail before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        SupplierDetail after = new SupplierDetail();
        after.setSupplierId(id);
        after.setSupplierName(request.getSupplierName());
        after.setQuotePrice(request.getQuotePrice());
        after.setSupplierContact(request.getSupplierContact());
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    @DeleteMapping("/supplier-detail/{id}")
    public Result<Void> deleteSupplierDetail(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    @GetMapping("/supplier-detail")
    public Result<PageInfo<SupplierDetail>> listSupplierDetail(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "supplier_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<SupplierDetail> list = service.listAll();
            PageInfo<SupplierDetail> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }

    @PostMapping("/supplier-detail/batch-delete")
    public Result<List<SupplierDetail>> batchDeleteSupplierDetail(
            @Valid @RequestBody BatchDeleteRequest request) {
        List<SupplierDetail> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    @PostMapping("/supplier-detail/analyze-risk/{id}")
    public Result<RiskAnalysis> analyzeRisk(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.analyzeRisk(id), "分析完成");
    }

    @GetMapping("/supplier-detail/risk-history/{id}")
    public Result<List<RiskAnalysis>> getRiskHistory(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.getHistory(id), "查询成功");
    }
}
