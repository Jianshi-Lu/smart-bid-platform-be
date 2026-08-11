package io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid.StatusUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.invitationforbid.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBidOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.InvitationForBidService;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordVectorIndexService;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招标公告 Controller。8 端点（6 标准 CRUD + 1 批量新增 + 1 状态变更）。端点详情见 {@code docs/api.md}（V18 重命名自 TenderNoticeController）。
 */
@RestController
public class InvitationForBidController {

    @Resource
    private InvitationForBidService service;

    @Resource
    private BidRecordVectorIndexService bidRecordVectorIndexService;

    @Resource
    private OperationHistoryService operationHistoryService;

    /**
     * 创建招标公告。Service 自动设 {@code createdAt} 和 {@code status=DRAFT}。
     *
     * @param request 创建请求（bidHandlerId / projectNo / purchaseTaskNo 必填）
     * @return 创建的实体（含自增 invitationId）
     */
    @PostMapping("/invitation-for-bid/add")
    public Result<InvitationForBid> createInvitationForBid(
            @Valid @RequestBody AddRequest request) {
        InvitationForBid entity = new InvitationForBid();
        entity.setBidHandlerId(request.getBidHandlerId());
        entity.setProjectNo(request.getProjectNo());
        entity.setPurchaseTaskNo(request.getPurchaseTaskNo());
        entity.setPlatformCategory(request.getPlatformCategory());
        entity.setTenderProcess(request.getTenderProcess());
        entity.setTenderMethod(request.getTenderMethod());
        entity.setPurchaseMethod(request.getPurchaseMethod());
        entity.setIsFixedPrice(request.getIsFixedPrice());
        entity.setHasDocumentFee(request.getHasDocumentFee());
        entity.setDeposit(request.getDeposit());
        entity.setCreditReportRequired(request.getCreditReportRequired());
        entity.setRegistrationDeadline(request.getRegistrationDeadline());
        entity.setTechnicalTenderDeadline(request.getTechnicalTenderDeadline());
        entity.setBusinessTenderDeadline(request.getBusinessTenderDeadline());
        entity.setNotes(request.getNotes());
        entity.setPurchaseCompany(request.getPurchaseCompany());
        entity.setPurchaseDepartment(request.getPurchaseDepartment());
        entity.setTenderingParty(request.getTenderingParty());
        service.add(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * 批量新增招标公告。单事务，最多 100 条。
     *
     * @param request 批量新增请求（items 非空，最多 100 条）
     * @return 创建的实体列表（含自增 invitationId）
     */
    @PostMapping("/invitation-for-bid/batch-add")
    public Result<List<InvitationForBid>> batchAddInvitationForBid(
            @Valid @RequestBody BatchAddRequest request) {
        List<InvitationForBid> created = service.batchAdd(request.getItems());
        return Result.success(created, "批量新增成功");
    }

    /**
     * 按 ID 查询招标公告。
     *
     * @param id 公告 ID
     * @return 实体（200）或 null（404）
     */
    @GetMapping("/invitation-for-bid/{id}")
    public Result<InvitationForBid> getByIdInvitationForBid(@PathVariable Integer id) {
        InvitationForBid entity = service.getById(id);
        if (entity == null) {
            throw invitationNotFound("READ", "LOAD_RECORD", id);
        }
        return Result.success(entity, "查询成功");
    }

    /**
     * 部分更新招标公告（PATCH 语义，null 字段不修改）。
     * <p>不支持修改 {@code status}，请使用 {@code POST /invitation-for-bid/status-update}。</p>
     *
     * @param id      公告 ID
     * @param request 更新请求（16 字段全可选）
     * @return Diff（更新前后对比）
     */
    @PatchMapping("/invitation-for-bid/{id}")
    public Result<Diff<InvitationForBid>> patchInvitationForBid(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        InvitationForBid before = service.getById(id);
        if (before == null) {
            throw invitationNotFound("UPDATE", "LOAD_RECORD", id);
        }
        InvitationForBid after = new InvitationForBid();
        after.setInvitationId(id);
        after.setBidHandlerId(request.getBidHandlerId());
        after.setProjectNo(request.getProjectNo());
        after.setPurchaseTaskNo(request.getPurchaseTaskNo());
        after.setPlatformCategory(request.getPlatformCategory());
        after.setTenderProcess(request.getTenderProcess());
        after.setTenderMethod(request.getTenderMethod());
        after.setPurchaseMethod(request.getPurchaseMethod());
        after.setIsFixedPrice(request.getIsFixedPrice());
        after.setHasDocumentFee(request.getHasDocumentFee());
        after.setDeposit(request.getDeposit());
        after.setCreditReportRequired(request.getCreditReportRequired());
        after.setRegistrationDeadline(request.getRegistrationDeadline());
        after.setTechnicalTenderDeadline(request.getTechnicalTenderDeadline());
        after.setBusinessTenderDeadline(request.getBusinessTenderDeadline());
        after.setNotes(request.getNotes());
        after.setPurchaseCompany(request.getPurchaseCompany());
        after.setPurchaseDepartment(request.getPurchaseDepartment());
        after.setTenderingParty(request.getTenderingParty());
        int rows = service.update(after);
        if (rows == 0) {
            throw invitationNotFound("UPDATE", "UPDATE_RECORD", id);
        }
        bidRecordVectorIndexService.reindexByInvitationId(id);
        return Result.success(
                new Diff<>(before, service.getById(id)), "更新成功");
    }

    /**
     * 按 ID 删除招标公告。
     *
     * @param id 公告 ID
     * @return 200（成功）或 404（不存在）
     */
    @DeleteMapping("/invitation-for-bid/{id}")
    public Result<Void> deleteInvitationForBid(@PathVariable Integer id) {
        int rows = service.delete(id);
        if (rows == 0) {
            throw invitationNotFound("DELETE", "DELETE_RECORD", id);
        }
        return Result.success(null, "删除成功");
    }

    /**
     * 分页查询招标公告列表。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort     排序字段（默认 invitation_id desc）
     * @return 分页结果（PageHelper ThreadLocal 模式，finally 确保清理）
     */
    @GetMapping("/invitation-for-bid")
    public Result<PageInfo<InvitationForBid>> listInvitationForBid(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "invitation_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<InvitationForBid> list = service.listAll();
            PageInfo<InvitationForBid> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 批量删除招标公告。永远返回 200，data 为实际删除的实体列表。
     *
     * @param request 批量删除请求（ids 最多 500 条）
     * @return 实际删除的实体列表
     */
    @PostMapping("/invitation-for-bid/batch-delete")
    public Result<List<InvitationForBid>> batchDeleteInvitationForBid(@Valid @RequestBody BatchDeleteRequest request) {
        List<InvitationForBid> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /**
     * 变更招标公告状态。状态机由 {@link io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus#canTransitionTo} 校验。
     *
     * @param request 状态变更请求（invitationId / status / remark）
     * @return 变更后的实体（含最新 status）
     */
    @PostMapping("/invitation-for-bid/status-update")
    public Result<InvitationForBid> updateInvitationForBidStatus(
            @Valid @RequestBody StatusUpdateRequest request) {
        InvitationForBid updated = service.transitionStatus(
                request.getInvitationId(), request.getStatus(), request.getRemark());
        bidRecordVectorIndexService.reindexByInvitationId(
                request.getInvitationId());
        return Result.success(updated, "状态变更成功");
    }

    /** 查询招标公告的操作历史（创建/修改/删除/状态变更，时间倒序）。 */
    @GetMapping("/invitation-for-bid/{id}/history")
    public Result<List<InvitationForBidOperationHistory>> listOperationHistory(
            @PathVariable Integer id) {
        return Result.success(
                operationHistoryService.listInvitationHistory(id), "查询成功");
    }

    /** 构造招标公告不存在异常，统一交给全局异常处理器返回。 */
    private AppException invitationNotFound(
            String operation,
            String stage,
            Integer id) {
        return new AppException(
                AppErrorCode.INVITATION_NOT_FOUND,
                "invitation",
                operation,
                stage,
                "招标公告不存在: " + id);
    }
}
