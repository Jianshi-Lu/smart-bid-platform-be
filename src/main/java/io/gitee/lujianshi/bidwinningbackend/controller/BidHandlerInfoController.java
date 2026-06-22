package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo.AddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo.BatchAddRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo.DeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo.UpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.service.bidhandlerinfo.BidHandlerInfoService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 经办人 Controller — 6 端点标准 CRUD
 *
 * <p>端点列表 / PATCH 语义 / 分页机制 / 批量删除 / AOP 邮件通知 / 7 层链路
 * 详见 {@code docs/business-controllers.md}。</p>
 */
@RestController
public class BidHandlerInfoController {

    @Resource
    private BidHandlerInfoService service;

    /**
     * <h2>创建经办人</h2>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code handlerName}</td><td>String</td><td>是</td><td>经办人姓名</td></tr>
     *   <tr><td>{@code companyName}</td><td>String</td><td>是</td><td>所属采购公司</td></tr>
     *   <tr><td>{@code departmentName}</td><td>String</td><td>是</td><td>所属部门</td></tr>
     *   <tr><td>{@code targetEmail}</td><td>String</td><td>是</td><td>通知邮件收件人（<b>已落库</b>）；空=返回400校验失败</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/add-bid-handler-info \
     *   -H "Content-Type: application/json" \
     *   -d '{"handlerName":"张三","companyName":"测试采购公司","departmentName":"采购部","targetEmail":"alice@example.com"}'
     * </pre>
     *
     * 创建成功后由 {@link BidHandlerInfoService#sendOperationEmail} 显式发送 HTML 通知邮件，
     * 收件人由 {@code targetEmail} 控制。</p>
     *
     * @param request 创建请求 DTO（{@link AddRequest}）
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
     * <h2>批量新增经办人</h2>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code targetEmail}</td><td>String</td><td>否</td><td>汇总邮件收件人；为空/blank 时不发送汇总邮件</td></tr>
     *   <tr><td>{@code items}</td><td>List&lt;AddRequest&gt;</td><td>是</td><td>经办人列表（1-100 条）</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/bid-handler-info/batch-add \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "targetEmail": "manager@company.com",
     *     "items": [
     *       {"handlerName":"张三","companyName":"北京科技","departmentName":"采购部","targetEmail":"zhang@x.com"},
     *       {"handlerName":"李四","companyName":"上海信息","departmentName":"招标部","targetEmail":"li@x.com"}
     *     ]
     *   }'
     * </pre>
     *
     * @param request 批量新增请求 DTO（{@link BatchAddRequest}）
     */
    @PostMapping("/bid-handler-info/batch-add")
    public Result<List<BidHandlerInfo>> batchAddBidHandlerInfo(@Valid @RequestBody BatchAddRequest request) {
        List<BidHandlerInfo> created = service.batchAdd(request.getItems());
        service.sendBatchAddEmail(created, request.getTargetEmail());
        return Result.success(created, "批量新增成功");
    }

    /**
     * <h2>按主键查询经办人</h2>
     *
     * <h3>输入参数（URL path）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>Integer</td><td>是</td><td>经办人主键 ID</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl http://localhost:8080/bid-handler-info/1</pre>
     *
     * @param id 主键 ID
     */
    @GetMapping("/bid-handler-info/{id}")
    public Result<BidHandlerInfo> getByIdBidHandlerInfo(@PathVariable Integer id) {
        BidHandlerInfo entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>按主键部分更新经办人（PATCH 语义）</h2>
     *
     * <h3>输入参数（URL path + JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>Integer（path）</td><td>是</td><td>经办人主键 ID</td></tr>
     *   <tr><td>{@code handlerName}</td><td>String</td><td>否</td><td>null=不修改</td></tr>
     *   <tr><td>{@code companyName}</td><td>String</td><td>否</td><td>null=不修改</td></tr>
     *   <tr><td>{@code departmentName}</td><td>String</td><td>否</td><td>null=不修改</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X PATCH http://localhost:8080/bid-handler-info/1 \
     *   -H "Content-Type: application/json" \
     *   -d '{"companyName":"新公司名"}'
     * </pre>
     *
     * @param id 主键 ID
     * @param request 更新请求 DTO（{@link UpdateRequest}）
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
        after.setHandlerId(id);
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
     * <h2>按主键删除经办人</h2>
     *
     * <h3>输入参数（URL path）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code id}</td><td>Integer</td><td>是</td><td>经办人主键 ID</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl -X DELETE http://localhost:8080/bid-handler-info/1</pre>
     *
     * @param id 主键 ID
     */
    @DeleteMapping("/bid-handler-info/{id}")
    public Result<Void> deleteBidHandlerInfo(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * <h2>列表查询经办人（分页）</h2>
     *
     * <h3>输入参数（URL query）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code page}</td><td>int</td><td>否</td><td>页码（默认 1，1-based）</td></tr>
     *   <tr><td>{@code pageSize}</td><td>int</td><td>否</td><td>每页条数（默认 3）</td></tr>
     *   <tr><td>{@code sort}</td><td>String</td><td>否</td><td>PageHelper 原生格式（空格分隔），默认 {@code "handler_id desc"}</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>curl "http://localhost:8080/bid-handler-info?page=1&pageSize=10&sort=handler_id%20desc"</pre>
     */
    @GetMapping("/bid-handler-info")
    public Result<PageInfo<BidHandlerInfo>> getAllBidHandlerInfo(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int pageSize,
            @RequestParam(defaultValue = "handler_id desc") String sort) {
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
     * <h2>批量删除经办人（按主键 ID 列表，p3 升级版）</h2>
     *
     * <h3>输入参数（JSON body）</h3>
     * <table border="1">
     *   <tr><th>字段</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>{@code ids}</td><td>List&lt;Integer&gt;</td><td>是</td><td>主键 ID 列表（@NotEmpty，@Size max=500）</td></tr>
     * </table>
     *
     * <h3>请求示例</h3>
     * <pre>
     * curl -X POST http://localhost:8080/bid-handler-info/batch-delete \
     *   -H "Content-Type: application/json" \
     *   -d '{"ids":[1,2,3]}'
     * </pre>
     *
     * @param request 批量删除请求 DTO（{@link DeleteRequest}）
     */
    @PostMapping("/bid-handler-info/batch-delete")
    public Result<List<BidHandlerInfo>> batchDeleteBidHandlerInfo(@Valid @RequestBody DeleteRequest request) {
        List<BidHandlerInfo> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }
}
