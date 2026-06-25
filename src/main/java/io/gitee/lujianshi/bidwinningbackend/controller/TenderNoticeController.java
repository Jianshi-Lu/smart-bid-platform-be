package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.tender.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tender.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tender.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tender.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderNotice;
import io.gitee.lujianshi.bidwinningbackend.service.TenderNoticeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招标公告 Controller。6 端点标准 CRUD。端点详情 / curl 示例见 {@code docs/api.md}。
 *
 * <p>注意：删除主表后 {@code supplier_detail} 中引用本表 {@code notice_id} 的行变孤儿
 * （应用层不保证完整性）。</p>
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
        service.add(entity);
        service.sendOperationEmail(entity);
        return Result.success(entity, "创建成功");
    }

    @PostMapping("/tender-notice/batch-add")
    public Result<List<TenderNotice>> batchAddTenderNotice(
            @Valid @RequestBody BatchAddRequest request) {
        List<TenderNotice> created = service.batchAdd(request.getItems());
        service.sendBatchAddEmail(created, request.getTargetEmail());
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
}
