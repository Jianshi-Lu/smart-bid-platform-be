package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailBatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailCreateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.service.bidwinningsupplierdetail.BidWinningSupplierDetailService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 中标结果明细 Controller — 6 端点标准 CRUD
 *
 * <p>端点列表 / PATCH 语义 / 分页机制 / 批量删除 / AOP 邮件通知 / 7 层链路
 * 详见 {@code docs/business-controllers.md}。</p>
 *
 * <p><b>逻辑外键</b>：{@code notificationId} → {@code bid_winning_notification.notificationId}
 * （DB 层无 FK 约束，引用不存在的主表 ID 会形成孤儿数据，应用层不保证完整性）。</p>
 */
@RestController
public class BidWinningSupplierDetailController {

    @Resource
    private BidWinningSupplierDetailService service;

    /**
     * <h2>创建中标结果明细</h2>
     *
     * <h3>输入参数（JSON body，3 字段全必填）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code notificationId}</td><td>Integer</td><td>是</td><td>所属定标通知 ID（逻辑外键）</td></tr>
     *   <tr><td>{@code supplierName}</td><td>String</td><td>是</td><td>中标供应商名称</td></tr>
     *   <tr><td>{@code amount}</td><td>BigDecimal</td><td>是</td><td>中标金额（元）</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/create-bid-winning-supplier-detail \
     *   -H "Content-Type: application/json" \
     *   -d '{"notificationId":1,"supplierName":"ABC公司","amount":1234567.89}'
     * </pre>
     *
     * @param request 创建请求 DTO（{@link BidWinningSupplierDetailCreateRequest}）
     */
    @PostMapping("/create-bid-winning-supplier-detail")
    public Result<BidWinningSupplierDetail> createBidWinningSupplierDetail(
            @RequestBody BidWinningSupplierDetailCreateRequest request) {
        BidWinningSupplierDetail entity = new BidWinningSupplierDetail();
        entity.setNotificationId(request.getNotificationId());
        entity.setSupplierName(request.getSupplierName());
        entity.setAmount(request.getAmount());
        service.create(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * <h2>按主键查询中标结果明细</h2>
     *
     * <h3>输入参数（URL path）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>Integer</td><td>是</td><td>中标结果明细主键 ID</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl http://localhost:8080/bid-winning-supplier-detail/1</pre>
     *
     * @param id 主键 ID
     */
    @GetMapping("/bid-winning-supplier-detail/{id}")
    public Result<BidWinningSupplierDetail> getByIdBidWinningSupplierDetail(@PathVariable Integer id) {
        BidWinningSupplierDetail entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>按主键部分更新中标结果明细（PATCH 语义，3 字段全可选）</h2>
     *
     * <h3>输入参数（URL path + JSON body）</h3>
     * <p>同创建端点 3 字段，但 <b>全部可选</b>，null 字段不修改。</p>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X PATCH http://localhost:8080/bid-winning-supplier-detail/1 \
     *   -H "Content-Type: application/json" \
     *   -d '{"amount":999999.99}'
     * </pre>
     *
     * @param id 主键 ID
     * @param request 更新请求 DTO（{@link BidWinningSupplierDetailUpdateRequest}）
     */
    @PatchMapping("/bid-winning-supplier-detail/{id}")
    public Result<Diff<BidWinningSupplierDetail>> patchBidWinningSupplierDetail(
            @PathVariable Integer id,
            @RequestBody BidWinningSupplierDetailUpdateRequest request) {
        BidWinningSupplierDetail before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        BidWinningSupplierDetail after = new BidWinningSupplierDetail();
        after.setDetailId(id);
        after.setNotificationId(request.getNotificationId());
        after.setSupplierName(request.getSupplierName());
        after.setAmount(request.getAmount());
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * <h2>按主键删除中标结果明细</h2>
     *
     * <h3>输入参数（URL path）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>Integer</td><td>是</td><td>中标结果明细主键 ID</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl -X DELETE http://localhost:8080/bid-winning-supplier-detail/1</pre>
     *
     * @param id 主键 ID
     */
    @DeleteMapping("/bid-winning-supplier-detail/{id}")
    public Result<Void> deleteBidWinningSupplierDetail(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>列表查询中标结果明细（分页）</h2>
     *
     * <h3>输入参数（URL query）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code pageNum}</td><td>int</td><td>否</td><td>页码（默认 1）</td></tr>
     *   <tr><td>{@code pageSize}</td><td>int</td><td>否</td><td>每页条数（默认 10）</td></tr>
     *   <tr><td>{@code sort}</td><td>String</td><td>否</td><td>PageHelper 原生格式（空格分隔），默认 {@code "detail_id desc"}</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl "http://localhost:8080/bid-winning-supplier-detail?pageNum=1&pageSize=10&sort=detail_id%20desc"</pre>
     */
    @GetMapping("/bid-winning-supplier-detail")
    public Result<PageInfo<BidWinningSupplierDetail>> listBidWinningSupplierDetail(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "detail_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(pageNum, pageSize, sort);
            List<BidWinningSupplierDetail> list = service.listAll();
            PageInfo<BidWinningSupplierDetail> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * <h2>批量删除中标结果明细（按主键 ID 列表，p3 升级版）</h2>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code ids}</td><td>List&lt;Integer&gt;</td><td>是</td><td>主键 ID 列表（@NotEmpty，@Size max=500）</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/bid-winning-supplier-detail/batch-delete \
     *   -H "Content-Type: application/json" \
     *   -d '{"ids":[1,2,3]}'
     * </pre>
     *
     * @param request 批量删除请求 DTO（{@link BidWinningSupplierDetailBatchDeleteRequest}）
     */
    @PostMapping("/bid-winning-supplier-detail/batch-delete")
    public Result<List<BidWinningSupplierDetail>> batchDeleteBidWinningSupplierDetail(
            @Valid @RequestBody BidWinningSupplierDetailBatchDeleteRequest request) {
        List<BidWinningSupplierDetail> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }
}
