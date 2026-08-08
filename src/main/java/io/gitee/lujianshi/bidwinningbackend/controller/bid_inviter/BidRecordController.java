package io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidrecord.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidrecord.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecordOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 投标记录 Controller。7 端点（查询/更新/删除/分页/批量删除/按公告过滤/历史）。
 *
 * <p>投标记录由投标人创建（/bidder/bid-records），招标方只做管理，不再提供创建端点。
 * 风险分析查询和触发入口已拆分到 {@link RiskAnalysisController}。</p>
 *
 * @since V18
 */
@RestController
public class BidRecordController {

    @Resource
    private BidRecordService service;

    @Resource
    private OperationHistoryService operationHistoryService;

    /**
     * 按 ID 查询投标记录。
     *
     * @param id 投标记录 ID
     * @return 实体（200）或 null（404）
     */
    @GetMapping("/bid-record/{id}")
    public Result<BidRecord> getBidRecord(@PathVariable Integer id) {
        BidRecord entity = service.getById(id);
        if (entity == null) {
            throw bidRecordNotFound("READ", "LOAD_RECORD", id);
        }
        return Result.success(entity, "查询成功");
    }

    /**
     * 部分更新投标记录（PATCH 语义，null 字段不修改）。
     * <p>不允许修改 {@code bidNo} / {@code bidAt}（由 Service 在 add 时自动算）。</p>
     *
     * @param id      投标记录 ID
     * @param request 更新请求（4 字段全可选，credentials 为 Markdown 路径列表）
     * @return Diff（更新前后对比）
     */
    @PatchMapping("/bid-record/{id}")
    public Result<Diff<BidRecord>> patchBidRecord(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        BidRecord before = service.getById(id);
        if (before == null) {
            throw bidRecordNotFound("UPDATE", "LOAD_RECORD", id);
        }
        BidRecord after = new BidRecord();
        after.setBidRecordId(id);
        after.setBidderName(request.getBidderName());
        after.setQuotePrice(request.getQuotePrice());
        after.setBidderContact(request.getBidderContact());
        after.setCredentials(request.getCredentials());
        int rows = service.update(after);
        if (rows == 0) {
            throw bidRecordNotFound("UPDATE", "UPDATE_RECORD", id);
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按 ID 删除投标记录。
     *
     * @param id 投标记录 ID
     * @return 200（成功）或 404（不存在）
     */
    @DeleteMapping("/bid-record/{id}")
    public Result<Void> deleteBidRecord(@PathVariable Integer id) {
        int rows = service.delete(id);
        if (rows == 0) {
            throw bidRecordNotFound("DELETE", "DELETE_RECORD", id);
        }
        return Result.success(null, "删除成功");
    }

    /**
     * 分页查询投标记录列表。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort     排序字段（默认 bid_record_id desc）
     * @return 分页结果（PageHelper ThreadLocal 模式，finally 确保清理）
     */
    @GetMapping("/bid-record")
    public Result<PageInfo<BidRecord>> listBidRecord(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "bid_record_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<BidRecord> list = service.listAll();
            PageInfo<BidRecord> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 批量删除投标记录。永远返回 200，data 为实际删除的实体列表。
     *
     * @param request 批量删除请求（ids 最多 500 条）
     * @return 实际删除的实体列表
     */
    @PostMapping("/bid-record/batch-delete")
    public Result<List<BidRecord>> batchDeleteBidRecord(@Valid @RequestBody BatchDeleteRequest request) {
        List<BidRecord> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /**
     * V18 新增：按招标公告 ID 查询所有投标记录（前端"查看投标人"场景）。
     *
     * @param invitationId 招标公告 ID
     * @return 该公告的所有投标记录（按 bidAt 升序），无记录时返回空列表
     */
    @GetMapping("/bid-record/by-invitation/{invitationId}")
    public Result<List<BidRecord>> listByInvitation(@PathVariable Integer invitationId) {
        return Result.success(service.listByInvitationId(invitationId), "查询成功");
    }

    /** 查询投标记录的操作历史（创建/修改/删除，时间倒序）。 */
    @GetMapping("/bid-record/{id}/history")
    public Result<List<BidRecordOperationHistory>> listOperationHistory(
            @PathVariable Integer id) {
        return Result.success(
                operationHistoryService.listBidRecordHistory(id), "查询成功");
    }

    /** 构造投标记录不存在异常，统一交给全局异常处理器返回。 */
    private AppException bidRecordNotFound(
            String operation,
            String stage,
            Integer id) {
        return new AppException(
                AppErrorCode.BID_RECORD_NOT_FOUND,
                "bid-record",
                operation,
                stage,
                "投标记录不存在: " + id);
    }

}
