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
 * 供应商明细 Controller — 8 端点（含 2 风险分析端点）
 *
 * <p>端点列表 / PATCH 语义 / 分页机制 / 批量删除 / Service 层邮件通知 / 7 层链路
 * 详见 {@code docs/business-controllers.md}。</p>
 */
@RestController
public class SupplierDetailController {

    @Resource
    private SupplierDetailService service;

    @Resource
    private RiskAnalysisService riskAnalysisService;

    /**
     * <h2>创建供应商明细</h2>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code supplierName}</td><td>String</td><td>是</td><td>供应商名称</td></tr>
     *   <tr><td>{@code quotePrice}</td><td>BigDecimal</td><td>是</td><td>报价（元）</td></tr>
     *   <tr><td>{@code supplierContact}</td><td>String</td><td>是</td><td>联系方式（邮箱/电话/微信）</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/add-supplier-detail \
     *   -H "Content-Type: application/json" \
     *   -d '{"supplierName":"ABC公司","quotePrice":1234567.89,"supplierContact":"zhangsan@example.com"}'
     * </pre>
     */
    @PostMapping("/add-supplier-detail")
    public Result<SupplierDetail> addSupplierDetail(
            @Valid @RequestBody AddRequest request) {
        SupplierDetail entity = new SupplierDetail();
        entity.setSupplierName(request.getSupplierName());
        entity.setQuotePrice(request.getQuotePrice());
        entity.setSupplierContact(request.getSupplierContact());
        service.add(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * <h2>按主键查询供应商明细</h2>
     *
     * <h3>请求示例</h3>
     * <pre>curl http://localhost:8080/supplier-detail/1</pre>
     */
    @GetMapping("/supplier-detail/{id}")
    public Result<SupplierDetail> getSupplierDetail(@PathVariable Integer id) {
        SupplierDetail entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>按主键部分更新供应商明细（PATCH 语义）</h2>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X PATCH http://localhost:8080/supplier-detail/1 \
     *   -H "Content-Type: application/json" \
     *   -d '{"quotePrice":999999.99}'
     * </pre>
     */
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

    /**
     * <h2>按主键删除供应商明细</h2>
     *
     * <h3>请求示例</h3>
     * <pre>curl -X DELETE http://localhost:8080/supplier-detail/1</pre>
     */
    @DeleteMapping("/supplier-detail/{id}")
    public Result<Void> deleteSupplierDetail(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>列表查询供应商明细（分页）</h2>
     *
     * <h3>请求示例</h3>
     * <pre>curl "http://localhost:8080/supplier-detail?page=1&pageSize=10&sort=supplier_id%20desc"</pre>
     */
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

    /**
     * <h2>批量删除供应商明细（按 ID 列表）</h2>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/supplier-detail/batch-delete \
     *   -H "Content-Type: application/json" \
     *   -d '{"ids":[1,2,3]}'
     * </pre>
     */
    @PostMapping("/supplier-detail/batch-delete")
    public Result<List<SupplierDetail>> batchDeleteSupplierDetail(
            @Valid @RequestBody BatchDeleteRequest request) {
        List<SupplierDetail> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /**
     * <h2>触发供应商风险分析</h2>
     *
     * <p>异步调用 AI 模型对指定供应商进行风险分析，分析完成后结果落库。</p>
     *
     * <h3>请求示例</h3>
     * <pre>curl -X POST <a href="http://localhost:8080/supplier-detail/1/analyze-risk">...</a></pre>
     */
    @PostMapping("/supplier-detail/analyze-risk/{id}")
    public Result<RiskAnalysis> analyzeRisk(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.analyzeRisk(id), "分析完成");
    }

    /**
     * <h2>查询供应商历史风险分析记录</h2>
     *
     * <p>查询指定供应商的所有历史风险分析记录，按分析时间倒序排列。
     * 当前版本返回空列表，逻辑待实现。</p>
     *
     * <h3>请求示例</h3>
     * <pre>curl <a href="http://localhost:8080/supplier-detail/1/risk-history">...</a></pre>
     */
    @GetMapping("/supplier-detail/risk-history/{id}")
    public Result<List<RiskAnalysis>> getRiskHistory(@PathVariable Integer id) {
        return Result.success(riskAnalysisService.getHistory(id), "查询成功");
    }
}
