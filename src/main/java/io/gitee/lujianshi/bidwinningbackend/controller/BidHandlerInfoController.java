package io.gitee.lujianshi.bidwinningbackend.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 经办人 Controller 层 — HTTP 入口
 *
 * <h3>链路位置（前端请求视角，从前到后）</h3>
 * <pre>
 *   前端 POST/GET/PATCH/DELETE
 *     ↓ Jackson 反序列化
 *   DTO (BidHandlerInfoCreateRequest / UpdateRequest)
 *     ↓ 字段拷贝
 *   Controller（本类，5 个端点）
 *     ↓ Service 调用
 *   Service (BidHandlerInfoService — 纯 mapper 转发)
 *     ↓ Mapper 调用
 *   Mapper (BidHandlerInfoMapper)
 *     ↓ XML 映射
 *   SQL (BidHandlerInfoMapper.xml)
 *     ↓ JDBC
 *   MySQL bid_handler_info 表
 * </pre>
 *
 * <h3>链路位置（后端开发视角，从下到上）</h3>
 * <pre>
 *   ① bid_handler_info 表
 *      ↓
 *   ② BidHandlerInfo Entity（含 @TableName / @TableId / @NotBlank / @NotNull）
 *      ↓
 *   ③ BidHandlerInfoMapper 接口
 *      ↓
 *   ④ BidHandlerInfoMapper.xml SQL（含动态 SQL 动态 SET）
 *      ↓
 *   ⑤ BidHandlerInfoService（纯 mapper 转发）
 *      ↓
 *   ⑥ BidHandlerInfoCreateRequest / UpdateRequest DTO
 *      ↓
 *   ⑦ 本 Controller
 * </pre>
 *
 * <h3>响应格式</h3>
 * <p>所有端点统一返回 {@link Result} 包装：</p>
 * <ul>
 *   <li>{@code code} — 业务状态码（200 成功，404 资源不存在，400 参数错误，其他按需扩展）</li>
 *   <li>{@code message} — 文字消息</li>
 *   <li>{@code data} — 业务数据（删除类操作时为 null；update 端点时为 Diff&lt;T&gt;；列表查询时为 PageInfo&lt;T&gt;）</li>
 * </ul>
 *
 * <h3>已实现端点（5 个）</h3>
 * <ul>
 *   <li>{@code POST   /create-bid-handler-info}     — 创建经办人</li>
 *   <li>{@code GET    /bid-handler-info/{id}}        — 按主键单条查询</li>
 *   <li>{@code PATCH  /bid-handler-info/{id}}        — 按主键部分更新（返回 before/after diff）</li>
 *   <li>{@code DELETE /bid-handler-info/{id}}        — 按主键删除</li>
 *   <li>{@code GET    /bid-handler-info}             — 列表查询（PageHelper 分页）</li>
 * </ul>
 *
 * <h3>PATCH 语义（RFC 5789）</h3>
 * <p>本 Controller 的 update 端点使用 {@code @PatchMapping} 而非 {@code @PutMapping}：</p>
 * <ul>
 *   <li>{@code PATCH} 用于<b>部分更新</b>：调用方传哪些字段就更新哪些字段</li>
 *   <li>未传的字段（{@code null}）在 DB 中保持原值，由 XML 动态 SQL（{@code <set> + <if test="xxx != null">}）实现</li>
 *   <li>与 {@code BidWinningNotificationController} 风格保持一致</li>
 * </ul>
 *
 * <h3>分页机制（列表查询）</h3>
 * <p>用 <b>PageHelper</b>（{@code com.github.pagehelper}）实现：</p>
 * <ul>
 *   <li>请求参数：{@code page}（默认 1）、{@code size}（默认 3）、{@code sort}（默认 {@code "handler_id desc"}，空格分隔）</li>
 *   <li>SQL 拦截：PageHelper 用 ThreadLocal 在 startPage 后拦截下一条 SELECT，自动追加 LIMIT</li>
 *   <li>ThreadLocal 清理：{@code finally { PageHelper.clearPage() }} 防止线程池污染</li>
 *   <li>异常处理：catch 任何异常返 {@code Result.error(400, "分页查询参数错误: ...")}</li>
 * </ul>
 *
 * <h3>待办</h3>
 * <ul>
 *   <li>当前 {@code createBidHandlerInfo} / {@code patchBidHandlerInfo} 未加 {@code @Valid} 校验，DTO 上 {@code @NotBlank}/{@code @Size} 暂未生效（待 GlobalExceptionHandler 一起补全）</li>
 *   <li>分页 sort 参数未来可加白名单（防 SQL 注入 / 防字段拼错），当前透传给 PageHelper（依赖其解析）</li>
 *   <li>统一异常处理（{@code GlobalExceptionHandler} 尚未实现）</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoUpdateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.Diff
 * @see io.gitee.lujianshi.bidwinningbackend.dto.Result
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo
 * @see io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper
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
     * 按主键 ID 部分更新经办人（PATCH 语义）
     *
     * <p>执行链路：</p>
     * <pre>
     *   PATCH /bid-handler-info/{id}（Content-Type: application/json）
     *     → @PathVariable 从 URL 路径读取 id
     *     → Jackson 反序列化 body → BidHandlerInfoUpdateRequest
     *     → service.getById(id) 获取原始 entity（作为 before）
     *     → 构造新 entity（id + 3 字段，null 字段不更新）
     *     → service.update(newEntity) → mapper.updateById → 动态 SQL（仅更新非 null 字段）
     *     → 包装为 Result&lt;Diff&lt;BidHandlerInfo&gt;&gt; 序列化为 JSON
     *   成功：code=200, data={before, after}, message="更新成功"
     *   失败：code=404, data=null, message="记录不存在"
     * </pre>
     *
     * @param id 经办人主键 ID（从 URL 路径 {@code {id}} 自动注入）
     * @param request 3 字段（handlerName/companyName/departmentName，null=不修改）
     * @return {@code Result<Diff<BidHandlerInfo>>} 包含 before（DB 原值）和 after（更新后）
     */
    @PatchMapping("/bid-handler-info/{id}")
    public Result<Diff<BidHandlerInfo>> patchBidHandlerInfo(
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
    @GetMapping("/bid-handler-info")
    public Result<PageInfo<BidHandlerInfo>> getAllBidHandlerInfo(
        @RequestParam(defaultValue = "1") int page,        // 1-based（PageHelper 默认）
        @RequestParam(defaultValue = "3") int size,
        @RequestParam(defaultValue = "handler_id desc") String sort) {  // PageHelper 原生格式：空格分隔

        try {
            // 1. 启动分页（ThreadLocal 模式，作用于下一次 MyBatis 查询）
            PageHelper.startPage(page, size, sort);

            // 2. 调用 Service（被 PageHelper 自动加 LIMIT + COUNT）
            List<BidHandlerInfo> list = service.listAll();

            return Result.success(new PageInfo<>(list), "查询成功");
        } catch (Exception e) {
            // 兜底：sort 字段名拼错 / page/size 越界等都会被 PageHelper 抛错
            return Result.error(400, "分页查询参数错误: " + e.getMessage());
        } finally {
            PageHelper.clearPage();   // 关键：清理 ThreadLocal
        }
    }
}
