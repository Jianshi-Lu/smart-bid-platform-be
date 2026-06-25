package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningNotificationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定标通知 Controller。6 端点标准 CRUD。端点详情 / curl 示例见 {@code docs/api.md}。
 *
 * <p>注意：删除主表后 {@code supplier_detail} 中引用本表 {@code notification_id} 的行变孤儿
 * （应用层不保证完整性）。</p>
 */
@RestController
public class BidWinningNotificationController {

    @Resource
    private BidWinningNotificationService service;

    @PostMapping("/bid-winning-notification/add")
    public Result<BidWinningNotification> createBidWinningNotification(
            @Valid @RequestBody AddRequest request) {
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
        service.add(entity);
        service.sendOperationEmail(entity);
        return Result.success(entity, "创建成功");
    }

    @PostMapping("/bid-winning-notification/batch-add")
    public Result<List<BidWinningNotification>> batchAddBidWinningNotification(
            @Valid @RequestBody BatchAddRequest request) {
        List<BidWinningNotification> created = service.batchAdd(request.getItems());
        service.sendBatchAddEmail(created, request.getTargetEmail());
        return Result.success(created, "批量新增成功");
    }

    @GetMapping("/bid-winning-notification/{id}")
    public Result<BidWinningNotification> getByIdBidWinningNotification(@PathVariable Integer id) {
        BidWinningNotification entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    @PatchMapping("/bid-winning-notification/{id}")
    public Result<Diff<BidWinningNotification>> patchBidWinningNotification(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
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

    @DeleteMapping("/bid-winning-notification/{id}")
    public Result<Void> deleteBidWinningNotification(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    @GetMapping("/bid-winning-notification")
    public Result<PageInfo<BidWinningNotification>> listBidWinningNotification(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "notification_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<BidWinningNotification> list = service.listAll();
            PageInfo<BidWinningNotification> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }

    @PostMapping("/bid-winning-notification/batch-delete")
    public Result<List<BidWinningNotification>> batchDeleteBidWinningNotification(@Valid @RequestBody BatchDeleteRequest request) {
        List<BidWinningNotification> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }
}
