package io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.DeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.bidhandler.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.entity.bidhandler.BidHandlerInfoOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招标经办人 Controller。7 端点（6 标准 CRUD + 1 批量新增）。端点详情 / curl 示例见 {@code docs/api.md}（V18 重命名自 TenderHandlerInfoController）。
 */
@RestController
public class BidHandlerInfoController {

    @Resource
    private BidHandlerInfoService service;

    @Resource
    private OperationHistoryService operationHistoryService;

    /**
     * 创建经办人。创建后异步发送通知邮件。
     *
     * @param request 创建请求（handlerName / companyName / departmentName / targetEmail 必填）
     * @return 创建的实体（含自增 bidHandlerId）
     */
    @PostMapping("/bid-handler-info/add")
    public Result<BidHandlerInfo> addBidHandlerInfo(@Valid @RequestBody AddRequest request) {
        BidHandlerInfo entity = new BidHandlerInfo();
        entity.setHandlerName(request.getHandlerName());
        entity.setCompanyName(request.getCompanyName());
        entity.setDepartmentName(request.getDepartmentName());
        entity.setTargetEmail(request.getTargetEmail());
        service.add(entity);
        service.sendOperationEmail(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * 批量新增经办人。单事务，最多 100 条。创建后异步发送批量通知邮件。
     *
     * @param request 批量新增请求（items + targetEmail）
     * @return 创建的实体列表（含自增 bidHandlerId）
     */
    @PostMapping("/bid-handler-info/batch-add")
    public Result<List<BidHandlerInfo>> batchAddBidHandlerInfo(@Valid @RequestBody BatchAddRequest request) {
        List<BidHandlerInfo> created = service.batchAdd(request.getItems());
        service.sendBatchAddEmail(created, request.getTargetEmail());
        return Result.success(created, "批量新增成功");
    }

    /**
     * 按 ID 查询经办人。
     *
     * @param id 经办人 ID
     * @return 实体（200）或 null（404）
     */
    @GetMapping("/bid-handler-info/{id}")
    public Result<BidHandlerInfo> getByIdBidHandlerInfo(@PathVariable Integer id) {
        BidHandlerInfo entity = service.getById(id);
        if (entity == null) {
            throw bidHandlerNotFound("READ", "LOAD_RECORD", id);
        }
        return Result.success(entity, "查询成功");
    }

    /**
     * 部分更新经办人（PATCH 语义，null 字段不修改）。
     *
     * @param id      经办人 ID
     * @param request 更新请求（4 字段全可选）
     * @return Diff（更新前后对比）
     */
    @PatchMapping("/bid-handler-info/{id}")
    public Result<Diff<BidHandlerInfo>> patchBidHandlerInfo(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        BidHandlerInfo before = service.getById(id);
        if (before == null) {
            throw bidHandlerNotFound("UPDATE", "LOAD_RECORD", id);
        }
        BidHandlerInfo after = new BidHandlerInfo();
        after.setBidHandlerId(id);
        after.setHandlerName(request.getHandlerName());
        after.setCompanyName(request.getCompanyName());
        after.setDepartmentName(request.getDepartmentName());
        after.setTargetEmail(request.getTargetEmail());
        int rows = service.update(after);
        if (rows == 0) {
            throw bidHandlerNotFound("UPDATE", "UPDATE_RECORD", id);
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按 ID 删除经办人。
     *
     * @param id 经办人 ID
     * @return 200（成功）或 404（不存在）
     */
    @DeleteMapping("/bid-handler-info/{id}")
    public Result<Void> deleteBidHandlerInfo(@PathVariable Integer id) {
        int rows = service.delete(id);
        if (rows == 0) {
            throw bidHandlerNotFound("DELETE", "DELETE_RECORD", id);
        }
        return Result.success(null, "删除成功");
    }

    /**
     * 分页查询经办人列表。
     *
     * @param page     页码（默认 1）
     * @param pageSize 每页条数（默认 3）
     * @param sort     排序字段（默认 bid_handler_id desc）
     * @return 分页结果（PageHelper ThreadLocal 模式，finally 确保清理）
     */
    @GetMapping("/bid-handler-info")
    public Result<PageInfo<BidHandlerInfo>> getAllBidHandlerInfo(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int pageSize,
            @RequestParam(defaultValue = "bid_handler_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<BidHandlerInfo> list = service.listAll();
            return Result.success(new PageInfo<>(list), "查询成功");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 批量删除经办人。永远返回 200，data 为实际删除的实体列表。
     *
     * @param request 批量删除请求（ids 最多 500 条）
     * @return 实际删除的实体列表
     */
    @PostMapping("/bid-handler-info/batch-delete")
    public Result<List<BidHandlerInfo>> batchDeleteBidHandlerInfo(@Valid @RequestBody DeleteRequest request) {
        List<BidHandlerInfo> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /** 查询招标经办人的操作历史（创建/修改/删除，时间倒序）。 */
    @GetMapping("/bid-handler-info/{id}/history")
    public Result<List<BidHandlerInfoOperationHistory>> listOperationHistory(
            @PathVariable Integer id) {
        return Result.success(
                operationHistoryService.listBidHandlerHistory(id), "查询成功");
    }

    /** 构造招标经办人不存在异常，统一交给全局异常处理器返回。 */
    private AppException bidHandlerNotFound(
            String operation,
            String stage,
            Integer id) {
        return new AppException(
                AppErrorCode.BID_HANDLER_NOT_FOUND,
                "bid-handler",
                operation,
                stage,
                "招标经办人不存在: " + id);
    }
}
