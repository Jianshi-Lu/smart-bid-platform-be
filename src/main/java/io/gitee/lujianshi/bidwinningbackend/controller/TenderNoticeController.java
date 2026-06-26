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

    @PostMapping("/tender-notice/batch-add")
    public Result<List<TenderNotice>> batchAddTenderNotice(
            @Valid @RequestBody BatchAddRequest request) {
        List<TenderNotice> created = service.batchAdd(request.getItems());
        return Result.success(created, "批量新增成功");
    }

    @GetMapping("/tender-notice/{id}")
    public Result<TenderNotice> getByIdTenderNotice(@PathVariable Integer id) {
        TenderNotice entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

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

    @DeleteMapping("/tender-notice/{id}")
    public Result<Void> deleteTenderNotice(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

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

    @PostMapping("/tender-notice/batch-delete")
    public Result<List<TenderNotice>> batchDeleteTenderNotice(@Valid @RequestBody BatchDeleteRequest request) {
        List<TenderNotice> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

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
