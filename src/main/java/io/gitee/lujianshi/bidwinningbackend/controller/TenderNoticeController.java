package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.tendernotice.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tendernotice.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tendernotice.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tendernotice.StatusUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tendernotice.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderNotice;
import io.gitee.lujianshi.bidwinningbackend.service.TenderNoticeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招标公告 Controller。8 端点（6 标准 CRUD + 1 批量新增 + 1 状态变更）。端点详情见 {@code docs/api.md}。
 */
@RestController
public class TenderNoticeController {

    @Resource
    private TenderNoticeService service;

    /**
     * 创建招标公告。Service 自动设 {@code createdAt} 和 {@code status=DRAFT}。
     *
     * @param request 创建请求（tenderHandlerId / projectNo / purchaseTaskNo 必填）
     * @return 创建的实体（含自增 noticeId）
     */
    @PostMapping("/tender-notice/add")
    public Result<TenderNotice> createTenderNotice(
            @Valid @RequestBody AddRequest request) {
        TenderNotice entity = new TenderNotice();
        entity.setTenderHandlerId(request.getTenderHandlerId());
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
     * @return 创建的实体列表（含自增 noticeId）
     */
    @PostMapping("/tender-notice/batch-add")
    public Result<List<TenderNotice>> batchAddTenderNotice(
            @Valid @RequestBody BatchAddRequest request) {
        List<TenderNotice> created = service.batchAdd(request.getItems());
        return Result.success(created, "批量新增成功");
    }

    /**
     * 按 ID 查询招标公告。
     *
     * @param id 公告 ID
     * @return 实体（200）或 null（404）
     */
    @GetMapping("/tender-notice/{id}")
    public Result<TenderNotice> getByIdTenderNotice(@PathVariable Integer id) {
        TenderNotice entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 部分更新招标公告（PATCH 语义，null 字段不修改）。
     * <p>不支持修改 {@code status}，请使用 {@code POST /tender-notice/status-update}。</p>
     *
     * @param id      公告 ID
     * @param request 更新请求（16 字段全可选）
     * @return Diff（更新前后对比）
     */
    @PatchMapping("/tender-notice/{id}")
    public Result<Diff<TenderNotice>> patchTenderNotice(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        TenderNotice before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        TenderNotice after = new TenderNotice();
        after.setNoticeId(id);
        after.setTenderHandlerId(request.getTenderHandlerId());
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
            return Result.error(404, "记录不存在");
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按 ID 删除招标公告。
     *
     * @param id 公告 ID
     * @return 200（成功）或 404（不存在）
     */
    @DeleteMapping("/tender-notice/{id}")
    public Result<Void> deleteTenderNotice(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 分页查询招标公告列表。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort     排序字段（默认 notice_id desc）
     * @return 分页结果（PageHelper ThreadLocal 模式，finally 确保清理）
     */
    @GetMapping("/tender-notice")
    public Result<PageInfo<TenderNotice>> listTenderNotice(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "notice_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<TenderNotice> list = service.listAll();
            PageInfo<TenderNotice> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
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
    @PostMapping("/tender-notice/batch-delete")
    public Result<List<TenderNotice>> batchDeleteTenderNotice(@Valid @RequestBody BatchDeleteRequest request) {
        List<TenderNotice> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /**
     * 变更招标公告状态。状态机由 {@link io.gitee.lujianshi.bidwinningbackend.enums.TenderNoticeStatus#canTransitionTo} 校验。
     *
     * @param request 状态变更请求（noticeId / status / remark）
     * @return 变更后的实体（含最新 status）
     */
    @PostMapping("/tender-notice/status-update")
    public Result<TenderNotice> updateTenderNoticeStatus(
            @Valid @RequestBody StatusUpdateRequest request) {
        try {
            TenderNotice updated = service.transitionStatus(
                    request.getNoticeId(), request.getStatus(), request.getRemark());
            return Result.success(updated, "状态变更成功");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
