package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.tenderhandler.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tenderhandler.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tenderhandler.DeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.tenderhandler.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.TenderHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.service.TenderHandlerInfoService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招标经办人 Controller。7 端点（6 标准 CRUD + 1 批量新增）。端点详情 / curl 示例见 {@code docs/api.md}。
 */
@RestController
public class TenderHandlerInfoController {

    @Resource
    private TenderHandlerInfoService service;

    @PostMapping("/tender-handler-info/add")
    public Result<TenderHandlerInfo> addTenderHandlerInfo(@Valid @RequestBody AddRequest request) {
        TenderHandlerInfo entity = new TenderHandlerInfo();
        entity.setHandlerName(request.getHandlerName());
        entity.setCompanyName(request.getCompanyName());
        entity.setDepartmentName(request.getDepartmentName());
        entity.setTargetEmail(request.getTargetEmail());
        service.add(entity);
        service.sendOperationEmail(entity);
        return Result.success(entity, "创建成功");
    }

    @PostMapping("/tender-handler-info/batch-add")
    public Result<List<TenderHandlerInfo>> batchAddTenderHandlerInfo(@Valid @RequestBody BatchAddRequest request) {
        List<TenderHandlerInfo> created = service.batchAdd(request.getItems());
        service.sendBatchAddEmail(created, request.getTargetEmail());
        return Result.success(created, "批量新增成功");
    }

    @GetMapping("/tender-handler-info/{id}")
    public Result<TenderHandlerInfo> getByIdTenderHandlerInfo(@PathVariable Integer id) {
        TenderHandlerInfo entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    @PatchMapping("/tender-handler-info/{id}")
    public Result<Diff<TenderHandlerInfo>> patchTenderHandlerInfo(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateRequest request) {
        TenderHandlerInfo before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        TenderHandlerInfo after = new TenderHandlerInfo();
        after.setTenderHandlerId(id);
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

    @DeleteMapping("/tender-handler-info/{id}")
    public Result<Void> deleteTenderHandlerInfo(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    @GetMapping("/tender-handler-info")
    public Result<PageInfo<TenderHandlerInfo>> getAllTenderHandlerInfo(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int pageSize,
            @RequestParam(defaultValue = "tender_handler_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(page, pageSize, sort);
            List<TenderHandlerInfo> list = service.listAll();
            return Result.success(new PageInfo<>(list), "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误: " + e.getMessage());
        } finally {
            PageHelper.clearPage();
        }
    }

    @PostMapping("/tender-handler-info/batch-delete")
    public Result<List<TenderHandlerInfo>> batchDeleteTenderHandlerInfo(@Valid @RequestBody DeleteRequest request) {
        List<TenderHandlerInfo> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }
}
