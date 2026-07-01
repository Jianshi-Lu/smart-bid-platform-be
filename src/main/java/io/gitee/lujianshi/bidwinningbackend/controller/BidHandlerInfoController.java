package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.DeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandler.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService;
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
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
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
            return Result.error(404, "记录不存在");
        }
        BidHandlerInfo after = new BidHandlerInfo();
        after.setBidHandlerId(id);
        after.setHandlerName(request.getHandlerName());
        after.setCompanyName(request.getCompanyName());
        after.setDepartmentName(request.getDepartmentName());
        after.setTargetEmail(request.getTargetEmail());
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
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
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
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
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误: " + e.getMessage());
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
}
