package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationBatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationCreateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import io.gitee.lujianshi.bidwinningbackend.service.bidwinningnotification.BidWinningNotificationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定标通知 Controller — 6 端点标准 CRUD
 *
 * <p>端点列表 / PATCH 语义 / 分页机制 / 批量删除 / Service 层邮件通知 / 7 层链路
 * 详见 {@code docs/business-controllers.md}。</p>
 */
@RestController
public class BidWinningNotificationController {

    @Resource
    private BidWinningNotificationService service;

    /**
     * <h2>创建定标通知</h2>
     *
     * <h3>输入参数（JSON body，17 字段 = 3 必填 + 14 可选）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code handlerId}</td><td>Integer</td><td>是</td><td>经办人 ID</td></tr>
     *   <tr><td>{@code projectNo}</td><td>String</td><td>是</td><td>项目编号</td></tr>
     *   <tr><td>{@code purchaseTaskNo}</td><td>String</td><td>是</td><td>采购任务编号</td></tr>
     *   <tr><td>{@code platformCategory}</td><td>String</td><td>否</td><td>平台采购类别</td></tr>
     *   <tr><td>{@code biddingProcess}</td><td>String</td><td>否</td><td>招标流程</td></tr>
     *   <tr><td>{@code biddingMethod}</td><td>String</td><td>否</td><td>招标方式</td></tr>
     *   <tr><td>{@code purchaseMethod}</td><td>String</td><td>否</td><td>采购方式</td></tr>
     *   <tr><td>{@code isFixedPrice}</td><td>Boolean</td><td>否</td><td>是否固定价</td></tr>
     *   <tr><td>{@code targetCost}</td><td>BigDecimal</td><td>否</td><td>目标成本（元）</td></tr>
     *   <tr><td>{@code documentFee}</td><td>BigDecimal</td><td>否</td><td>文件费（元）</td></tr>
     *   <tr><td>{@code deposit}</td><td>BigDecimal</td><td>否</td><td>保证金（元）</td></tr>
     *   <tr><td>{@code creditReportRequired}</td><td>Boolean</td><td>否</td><td>是否需要资信报告</td></tr>
     *   <tr><td>{@code registrationDeadline}</td><td>LocalDateTime</td><td>否</td><td>注册截止时间</td></tr>
     *   <tr><td>{@code techBidDeadline}</td><td>LocalDateTime</td><td>否</td><td>技术标截止时间</td></tr>
     *   <tr><td>{@code businessBidDeadline}</td><td>LocalDateTime</td><td>否</td><td>商务标截止时间</td></tr>
     *   <tr><td>{@code biddingStatus}</td><td>String</td><td>否</td><td>招标状态</td></tr>
     *   <tr><td>{@code notes}</td><td>String</td><td>否</td><td>备注</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/create-bid-winning-notification \
     *   -H "Content-Type: application/json" \
     *   -d '{"handlerId":1,"projectNo":"PRJ-2024-001","purchaseTaskNo":"PT-2024-001"}'
     * </pre>
     *
     * <p>本端点当前不发送邮件通知。如需接入，参考
     * {@link io.gitee.lujianshi.bidwinningbackend.service.bidhandlerinfo.BidHandlerInfoService#sendOperationEmail}
     * 的实现模式（Service 层显式调用，由 BaseService.sendEmail 实现邮件发送）。</p>
     *
     * @param request 创建请求 DTO（{@link BidWinningNotificationCreateRequest}）
     */
    @PostMapping("/create-bid-winning-notification")
    public Result<BidWinningNotification> createBidWinningNotification(
            @RequestBody BidWinningNotificationCreateRequest request) {
        BidWinningNotification entity = new BidWinningNotification();
        entity.setHandlerId(request.getHandlerId());
        entity.setProjectNo(request.getProjectNo());
        entity.setPurchaseTaskNo(request.getPurchaseTaskNo());
        entity.setPlatformCategory(request.getPlatformCategory());
        entity.setBiddingProcess(request.getBiddingProcess());
        entity.setBiddingMethod(request.getBiddingMethod());
        entity.setPurchaseMethod(request.getPurchaseMethod());
        entity.setIsFixedPrice(request.getIsFixedPrice());
        entity.setTargetCost(request.getTargetCost());
        entity.setDocumentFee(request.getDocumentFee());
        entity.setDeposit(request.getDeposit());
        entity.setCreditReportRequired(request.getCreditReportRequired());
        entity.setRegistrationDeadline(request.getRegistrationDeadline());
        entity.setTechBidDeadline(request.getTechBidDeadline());
        entity.setBusinessBidDeadline(request.getBusinessBidDeadline());
        entity.setBiddingStatus(request.getBiddingStatus());
        entity.setNotes(request.getNotes());
        entity.setTargetEmail(request.getTargetEmail());
        service.create(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * <h2>按主键查询定标通知</h2>
     *
     * <h3>输入参数（URL path）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>Integer</td><td>是</td><td>定标通知主键 ID</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl http://localhost:8080/bid-winning-notification/1</pre>
     *
     * @param id 主键 ID
     */
    @GetMapping("/bid-winning-notification/{id}")
    public Result<BidWinningNotification> getByIdBidWinningNotification(@PathVariable Integer id) {
        BidWinningNotification entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>按主键部分更新定标通知（PATCH 语义，17 字段全可选）</h2>
     *
     * <h3>输入参数（URL path + JSON body）</h3>
     * <p>同创建端点 17 字段，但 <b>全部可选</b>，null 字段不修改。详见创建端点的字段表。</p>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X PATCH http://localhost:8080/bid-winning-notification/1 \
     *   -H "Content-Type: application/json" \
     *   -d '{"biddingStatus":"已完成"}'
     * </pre>
     *
     * @param id 主键 ID
     * @param request 更新请求 DTO（{@link BidWinningNotificationUpdateRequest}）
     */
    @PatchMapping("/bid-winning-notification/{id}")
    public Result<Diff<BidWinningNotification>> patchBidWinningNotification(
            @PathVariable Integer id,
            @RequestBody BidWinningNotificationUpdateRequest request) {
        BidWinningNotification before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        BidWinningNotification after = new BidWinningNotification();
        after.setNotificationId(id);
        after.setHandlerId(request.getHandlerId());
        after.setProjectNo(request.getProjectNo());
        after.setPurchaseTaskNo(request.getPurchaseTaskNo());
        after.setPlatformCategory(request.getPlatformCategory());
        after.setBiddingProcess(request.getBiddingProcess());
        after.setBiddingMethod(request.getBiddingMethod());
        after.setPurchaseMethod(request.getPurchaseMethod());
        after.setIsFixedPrice(request.getIsFixedPrice());
        after.setTargetCost(request.getTargetCost());
        after.setDocumentFee(request.getDocumentFee());
        after.setDeposit(request.getDeposit());
        after.setCreditReportRequired(request.getCreditReportRequired());
        after.setRegistrationDeadline(request.getRegistrationDeadline());
        after.setTechBidDeadline(request.getTechBidDeadline());
        after.setBusinessBidDeadline(request.getBusinessBidDeadline());
        after.setBiddingStatus(request.getBiddingStatus());
        after.setNotes(request.getNotes());
        after.setTargetEmail(request.getTargetEmail());
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * <h2>按主键删除定标通知</h2>
     *
     * <h3>输入参数（URL path）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>Integer</td><td>是</td><td>定标通知主键 ID</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl -X DELETE http://localhost:8080/bid-winning-notification/1</pre>
     *
     * <p><b>注意</b>：删除主表后 {@code bid_winning_supplier_detail} 中引用本表 {@code notification_id} 的行会变孤儿（应用层不保证完整性）。</p>
     *
     * @param id 主键 ID
     */
    @DeleteMapping("/bid-winning-notification/{id}")
    public Result<Void> deleteBidWinningNotification(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>列表查询定标通知（分页）</h2>
     *
     * <h3>输入参数（URL query）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code pageNum}</td><td>int</td><td>否</td><td>页码（默认 1）</td></tr>
     *   <tr><td>{@code pageSize}</td><td>int</td><td>否</td><td>每页条数（默认 10）</td></tr>
     *   <tr><td>{@code sort}</td><td>String</td><td>否</td><td>PageHelper 原生格式（空格分隔），默认 {@code "notification_id desc"}</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl "http://localhost:8080/bid-winning-notification?pageNum=1&pageSize=10&sort=notification_id%20desc"</pre>
     */
    @GetMapping("/bid-winning-notification")
    public Result<PageInfo<BidWinningNotification>> listBidWinningNotification(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "notification_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(pageNum, pageSize, sort);
            List<BidWinningNotification> list = service.listAll();
            PageInfo<BidWinningNotification> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * <h2>批量删除定标通知（按主键 ID 列表，p3 升级版）</h2>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code ids}</td><td>List&lt;Integer&gt;</td><td>是</td><td>主键 ID 列表（@NotEmpty，@Size max=500）</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/bid-winning-notification/batch-delete \
     *   -H "Content-Type: application/json" \
     *   -d '{"ids":[1,2,3]}'
     * </pre>
     *
     * @param request 批量删除请求 DTO（{@link BidWinningNotificationBatchDeleteRequest}）
     */
    @PostMapping("/bid-winning-notification/batch-delete")
    public Result<List<BidWinningNotification>> batchDeleteBidWinningNotification(@Valid @RequestBody BidWinningNotificationBatchDeleteRequest request) {
        List<BidWinningNotification> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }
}
