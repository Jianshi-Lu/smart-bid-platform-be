package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 经办人 Controller 层 — HTTP 入口
 *
 * <h3>链路位置（前端请求视角，从前到后）</h3>
 * <pre>
 *   前端 POST JSON
 *     ↓ Jackson 反序列化
 *   DTO (BidHandlerInfoCreateRequest)
 *     ↓ 字段拷贝
 *   Controller（本类，createBidHandlerInfo）
 *     ↓ Service 调用
 *   Service (BidHandlerInfoService.create)
 *     ↓ Mapper 调用
 *   Mapper (BidHandlerInfoMapper.insert)
 *     ↓ XML 映射
 *   SQL (BidHandlerInfoMapper.xml &lt;insert&gt;)
 *     ↓ JDBC
 *   MySQL bid_handler_info 表
 * </pre>
 *
 * <h3>链路位置（后端开发视角，从下到上）</h3>
 * <pre>
 *   ① bid_handler_info 表
 *      ↓
 *   ② BidHandlerInfo Entity
 *      ↓
 *   ③ BidHandlerInfoMapper 接口
 *      ↓
 *   ④ BidHandlerInfoMapper.xml SQL
 *      ↓
 *   ⑤ BidHandlerInfoService
 *      ↓
 *   ⑥ BidHandlerInfoCreateRequest DTO
 *      ↓
 *   ⑦ 本 Controller
 * </pre>
 *
 * <h3>响应格式</h3>
 * <p>所有端点统一返回 {@link Result} 包装：</p>
 * <ul>
 *   <li>{@code code} — 业务状态码（200 成功，404 资源不存在，其他按需扩展）</li>
 *   <li>{@code message} — 文字消息</li>
 *   <li>{@code data} — 业务数据（删除类操作时为 null）</li>
 * </ul>
 *
 * <h3>已实现端点</h3>
 * <ul>
 *   <li>{@code POST   /create-bid-handler-info}     — 创建经办人</li>
 *   <li>{@code DELETE /bid-handler-info/{id}}        — 按主键删除经办人</li>
 *   <li>{@code PUT    /bid-handler-info/{id}}        — 按主键更新经办人（返回 before/after diff）</li>
 *   <li>{@code GET    /bid-handler-info/{id}}        — 按主键单条查询</li>
 * </ul>
 *
 * <h3>待实现端点（查）</h3>
 * <ul>
 *   <li>{@code GET    /bid-handler-info}             — 列表查询（<b>待设计分页机制</b>）</li>
 * </ul>
 *
 * <h3>待办</h3>
 * <ul>
 *   <li>实现列表查询端点（需先设计分页机制：MyBatis-Plus 分页插件 / 手动 LIMIT / PageHelper）</li>
 *   <li>当前 {@code createBidHandlerInfo} / {@code updateBidHandlerInfo} 未加 {@code @Valid} 校验，DTO 上的 {@code @NotBlank}/{@code @Size} 暂未生效</li>
 *   <li>统一异常处理（{@code GlobalExceptionHandler} 尚未实现）</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoUpdateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.Diff
 * @see io.gitee.lujianshi.bidwinningbackend.dto.Result
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo
 * @see io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService
 */
@RestController
public class BidHandlerInfoController {

    @Resource
    private BidHandlerInfoService service;

    /**
     * 创建经办人
     *
     * <p>执行链路：</p>
     * <pre>
     *   POST /create-bid-handler-info（Content-Type: application/json）
     *     → Jackson 反序列化 JSON → BidHandlerInfoCreateRequest
     *     → DTO 字段拷贝到 Entity
     *     → service.create(entity)
     *         → mapper.insert(entity) → SQL INSERT → MySQL 自增回填 handlerId
     *     → 包装为 Result&lt;BidHandlerInfo&gt; 序列化为 JSON
     * </pre>
     *
     * @param request 前端传入的经办人创建参数（{@code @NotBlank}/{@code @Size} 校验定义在 DTO 上，**当前未启用 @Valid，需后续补充**）
     * @return {@code Result<BidHandlerInfo>} 包含 data（Entity）和 message（"创建成功"）
     */
    @PostMapping("/create-bid-handler-info")
    public Result<BidHandlerInfo> createBidHandlerInfo(@RequestBody BidHandlerInfoCreateRequest request) {
        BidHandlerInfo entity = new BidHandlerInfo();
        entity.setHandlerName(request.getHandlerName());
        entity.setCompanyName(request.getCompanyName());
        entity.setDepartmentName(request.getDepartmentName());
        service.create(entity);

        return Result.success(entity, "创建成功");
    }

    // TODO: 2. 批量删除 3. 改 4. 查
    /**
     * 按主键 ID 删除经办人
     *
     * <p>执行链路：</p>
     * <pre>
     *   DELETE /bid-handler-info/{id}（无需 body）
     *     → @PathVariable 从 URL 路径读取 id
     *     → service.delete(id) → mapper.deleteById(id) → SQL DELETE
     *     → 包装为 Result&lt;Void&gt; 序列化为 JSON
     *   成功：code=200, message="删除成功", data=null
     *   失败：code=404, message="记录不存在", data=null
     * </pre>
     *
     * @param id 经办人主键 ID（从 URL 路径 {@code {id}} 自动注入）
     * @return {@code Result<Void>} — code=200 删除成功 / code=404 记录不存在
     */
    @DeleteMapping("/bid-handler-info/{id}")
    public Result<Void> deleteBidHandlerInfo(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")    // 1=成功
                : Result.error(404, "记录不存在");    // 0=记录不存在
    }

    /**
     * 按主键 ID 更新经办人
     *
     * <p>执行链路：</p>
     * <pre>
     *   PUT /bid-handler-info/{id}（Content-Type: application/json）
     *     → @PathVariable 从 URL 路径读取 id
     *     → Jackson 反序列化 body → BidHandlerInfoUpdateRequest
     *     → service.getById(id) 获取原始 entity（作为 before）
     *     → 构造新 entity（id + 3 字段）
     *     → service.update(newEntity) → mapper.updateById → SQL UPDATE
     *     → 包装为 Result&lt;Diff&lt;BidHandlerInfo&gt;&gt; 序列化为 JSON
     *   成功：code=200, data={before, after}, message="更新成功"
     *   失败：code=404, data=null, message="记录不存在"
     * </pre>
     *
     * @param id 经办人主键 ID（从 URL 路径 {@code {id}} 自动注入）
     * @param request 新值（3 字段：handlerName/companyName/departmentName）
     * @return {@code Result<Diff<BidHandlerInfo>>} 包含 before（DB 原值）和 after（更新后）
     */
    @PutMapping("/bid-handler-info/{id}")
    public Result<Diff<BidHandlerInfo>> updateBidHandlerInfo(
            @PathVariable Integer id,
            @RequestBody BidHandlerInfoUpdateRequest request) {
        // 1. 先查原始 entity（diff.before 来源）
        BidHandlerInfo before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }

        // 2. 构造新 entity（id 来自 path，3 字段来自 body）— 同时作为 diff.after
        BidHandlerInfo after = new BidHandlerInfo();
        after.setHandlerId(id);
        after.setHandlerName(request.getHandlerName());
        after.setCompanyName(request.getCompanyName());
        after.setDepartmentName(request.getDepartmentName());

        // 3. 调用 service 更新
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }

        // 4. 包装 diff 返回
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按主键 ID 查询单条经办人
     *
     * <p>执行链路：</p>
     * <pre>
     *   GET /bid-handler-info/{id}（无需 body，无需认证参数）
     *     → @PathVariable 从 URL 路径读取 id
     *     → service.getById(id) → mapper.selectById(id) → SQL SELECT
     *     → 包装为 Result&lt;BidHandlerInfo&gt; 序列化为 JSON
     *   成功：code=200, data=entity, message="查询成功"
     *   失败：code=404, data=null, message="记录不存在"
     * </pre>
     *
     * @param id 经办人主键 ID（从 URL 路径 {@code {id}} 自动注入）
     * @return {@code Result<BidHandlerInfo>} — code=200 成功（含 entity） / code=404 记录不存在
     */
    @GetMapping("/bid-handler-info/{id}")
    public Result<BidHandlerInfo> getByIdBidHandlerInfo(@PathVariable Integer id) {
        BidHandlerInfo entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")    // 找到
                : Result.error(404, "记录不存在");   // 未找到
    }
}
