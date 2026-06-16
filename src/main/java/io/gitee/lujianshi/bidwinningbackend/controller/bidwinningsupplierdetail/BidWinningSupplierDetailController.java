package io.gitee.lujianshi.bidwinningbackend.controller.bidwinningsupplierdetail;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.annotation.SendEmailOnSuccess;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailBatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailCreateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.service.bidwinningsupplierdetail.BidWinningSupplierDetailService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中标结果明细 Controller 层 — HTTP 入口
 *
 * <h3>链路位置（前端请求视角，从前到后）</h3>
 * <pre>
 *   前端 POST/PATCH/DELETE/GET
 *     ↓ Jackson 反序列化
 *   DTO (BidWinningSupplierDetailCreateRequest / UpdateRequest / BatchDeleteRequest)
 *     ↓ 字段拷贝
 *   Controller（本类，6 个端点）
 *     ↓ Service 调用
 *   Service (BidWinningSupplierDetailService — 纯 mapper 转发 + @Transactional)
 *     ↓ Mapper 调用
 *   Mapper (BidWinningSupplierDetailMapper)
 *     ↓ XML 映射
 *   SQL (BidWinningSupplierDetailMapper.xml)
 *     ↓ JDBC
 *   MySQL bid_winning_supplier_detail 表
 * </pre>
 *
 * <h3>链路位置（后端开发视角，从下到上）</h3>
 * <pre>
 *   ① bid_winning_supplier_detail 表
 *      ↓
 *   ② BidWinningSupplierDetail Entity（含 @TableName / @TableId）
 *      ↓
 *   ③ BidWinningSupplierDetailMapper 接口
 *      ↓
 *   ④ BidWinningSupplierDetailMapper.xml SQL（PATCH 动态 SET + deleteByIds + selectByIds）
 *      ↓
 *   ⑤ BidWinningSupplierDetailService（4 写方法 @Transactional，读方法无）
 *      ↓
 *   ⑥ BidWinningSupplierDetailCreateRequest / UpdateRequest / BatchDeleteRequest DTO
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
 * <h3>已实现端点（6 个）</h3>
 * <ul>
 *   <li>{@code POST   /create-bid-winning-supplier-detail}     — 创建中标结果明细</li>
 *   <li>{@code GET    /bid-winning-supplier-detail/{id}}        — 按主键单条查询</li>
 *   <li>{@code PATCH  /bid-winning-supplier-detail/{id}}        — 按主键部分更新（返回 before/after diff）</li>
 *   <li>{@code DELETE /bid-winning-supplier-detail/{id}}        — 按主键删除</li>
 *   <li>{@code GET    /bid-winning-supplier-detail}             — 列表查询（PageHelper 分页）</li>
 *   <li>{@code POST   /bid-winning-supplier-detail/batch-delete} — 按主键 ID 列表批量删除（p3 升级版，data 返实体列表）</li>
 * </ul>
 *
 * <h3>PATCH 语义（RFC 5789）</h3>
 * <p>本 Controller 的 update 端点使用 {@code @PatchMapping} 而非 {@code @PutMapping}：</p>
 * <ul>
 *   <li>{@code PATCH} 用于<b>部分更新</b>：调用方传哪些字段就更新哪些字段</li>
 *   <li>未传的字段（{@code null}）在 DB 中保持原值，由 XML 动态 SQL（{@code <set> + <if test="xxx != null">}）实现</li>
 *   <li>与 {@code BidHandlerInfoController} / {@code BidWinningNotificationController} 风格保持一致</li>
 * </ul>
 *
 * <h3>分页机制（列表查询）</h3>
 * <p>用 <b>PageHelper</b>（{@code com.github.pagehelper}）实现：</p>
 * <ul>
 *   <li>请求参数：{@code pageNum}（默认 1）、{@code pageSize}（默认 10）、{@code sort}（默认 {@code "detail_id desc"}）</li>
 *   <li>SQL 拦截：PageHelper 用 ThreadLocal 在 startPage 后拦截下一条 SELECT，自动追加 LIMIT</li>
 *   <li>ThreadLocal 清理：{@code finally { PageHelper.clearPage() }} 防止线程池污染</li>
 *   <li>异常处理：catch 任何异常返 {@code Result.error(400, "分页查询参数错误")}（不暴露 e.getMessage()）</li>
 * </ul>
 *
 * <h3>批量删除（p3 升级版）</h3>
 * <ul>
 *   <li>永远 200，<b>不</b>区分"全部不存在"与"部分不存在"</li>
 *   <li>{@code data} = 实际删除的实体列表（前端展示用）</li>
 *   <li>不存在的 ID：不在 data 中，前端 diff 自己请求的 ids 即可</li>
 *   <li><b>多 1 次 SELECT</b>（PK 索引，毫秒级）</li>
 * </ul>
 *
 * <h3>逻辑外键</h3>
 * <ul>
 *   <li>{@code notification_id} → {@code bid_winning_notification.notification_id}（<b>逻辑外键</b>，DB 层无 FK 约束）</li>
 *   <li>删除主表后明细行变孤儿（应用层不保证完整性，p3 升级版不做清理）</li>
 * </ul>
 *
 * <h3>待办</h3>
 * <ul>
 *   <li>当前 {@code createBidWinningSupplierDetail} / {@code patchBidWinningSupplierDetail} 未加 {@code @Valid}，DTO 上校验注解暂未生效（待 GlobalExceptionHandler 一起补全）</li>
 *   <li>分页 sort 参数未来可加白名单（防 SQL 注入 / 防字段拼错），当前透传给 PageHelper（依赖其解析）</li>
 *   <li>统一异常处理（{@code GlobalExceptionHandler} 尚未实现）</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailUpdateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail.BidWinningSupplierDetailBatchDeleteRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Diff
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Result
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail
 * @see io.gitee.lujianshi.bidwinningbackend.service.bidwinningsupplierdetail.BidWinningSupplierDetailService
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningSupplierDetailMapper
 */
@RestController
public class BidWinningSupplierDetailController {

    @Resource
    private BidWinningSupplierDetailService service;

    /**
     * 创建中标结果明细
     *
     * <p>执行链路：</p>
     * <pre>
     *   POST /create-bid-winning-supplier-detail（Content-Type: application/json）
     *     → Jackson 反序列化 JSON → BidWinningSupplierDetailCreateRequest
     *     → DTO 字段拷贝到 Entity
     *     → service.create(entity)
     *         → mapper.insert(entity) → SQL INSERT → MySQL 自增回填 detailId
     *     → 包装为 Result&lt;BidWinningSupplierDetail&gt; 序列化为 JSON
     * </pre>
     *
     * @param request 前端传入的创建参数（3 字段含 @NotNull/@NotBlank/@Size 校验，**当前未启用 @Valid**）
     * @return {@code Result<BidWinningSupplierDetail>} 包含 data（Entity）和 message（"创建成功"）
     */
    // 业务操作成功后通过 AOP 切面（CrudResultEmailAspect）异步发送邮件通知
    @SendEmailOnSuccess(to = "ops@company.com")
    @PostMapping("/create-bid-winning-supplier-detail")
    public Result<BidWinningSupplierDetail> createBidWinningSupplierDetail(
            @RequestBody BidWinningSupplierDetailCreateRequest request) {
        BidWinningSupplierDetail entity = new BidWinningSupplierDetail();
        entity.setNotificationId(request.getNotificationId());
        entity.setSupplierName(request.getSupplierName());
        entity.setAmount(request.getAmount());
        service.create(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * 按主键 ID 查询单条中标结果明细
     *
     * <p>执行链路：</p>
     * <pre>
     *   GET /bid-winning-supplier-detail/{id}（无需 body）
     *     → @PathVariable 从 URL 路径读取 id
     *     → service.getById(id) → mapper.selectById(id) → SQL SELECT
     *     → 包装为 Result&lt;BidWinningSupplierDetail&gt; 序列化为 JSON
     *   成功：code=200, data=entity, message="查询成功"
     *   失败：code=404, data=null, message="记录不存在"
     * </pre>
     *
     * @param id 主键 ID（从 URL 路径 {@code {id}} 自动注入）
     * @return {@code Result<BidWinningSupplierDetail>} — code=200 成功（含 entity） / code=404 记录不存在
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @GetMapping("/bid-winning-supplier-detail/{id}")
    public Result<BidWinningSupplierDetail> getByIdBidWinningSupplierDetail(@PathVariable Integer id) {
        BidWinningSupplierDetail entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 按主键 ID 部分更新中标结果明细（PATCH 语义）
     *
     * <p>执行链路：</p>
     * <pre>
     *   PATCH /bid-winning-supplier-detail/{id}（Content-Type: application/json）
     *     → @PathVariable 从 URL 路径读取 id
     *     → Jackson 反序列化 body → BidWinningSupplierDetailUpdateRequest
     *     → service.getById(id) 获取原始 entity（作为 before）
     *     → 构造新 entity（id + 3 字段，null 字段不更新）
     *     → service.update(newEntity) → mapper.updateById → 动态 SQL（仅更新非 null 字段）
     *     → 包装为 Result&lt;Diff&lt;BidWinningSupplierDetail&gt;&gt; 序列化为 JSON
     *   成功：code=200, data={before, after}, message="更新成功"
     *   失败：code=404, data=null, message="记录不存在"
     * </pre>
     *
     * @param id 主键 ID（从 URL 路径 {@code {id}} 自动注入）
     * @param request 3 字段（null=不修改）
     * @return {@code Result<Diff<BidWinningSupplierDetail>>} 包含 before（DB 原值）和 after（更新后）
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @PatchMapping("/bid-winning-supplier-detail/{id}")
    public Result<Diff<BidWinningSupplierDetail>> patchBidWinningSupplierDetail(
            @PathVariable Integer id,
            @RequestBody BidWinningSupplierDetailUpdateRequest request) {
        BidWinningSupplierDetail before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }
        BidWinningSupplierDetail after = new BidWinningSupplierDetail();
        after.setDetailId(id);
        after.setNotificationId(request.getNotificationId());
        after.setSupplierName(request.getSupplierName());
        after.setAmount(request.getAmount());
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按主键 ID 删除中标结果明细
     *
     * <p>执行链路：</p>
     * <pre>
     *   DELETE /bid-winning-supplier-detail/{id}（无需 body）
     *     → @PathVariable 从 URL 路径读取 id
     *     → service.delete(id) → mapper.deleteById(id) → SQL DELETE
     *     → 包装为 Result&lt;Void&gt; 序列化为 JSON
     *   成功：code=200, message="删除成功", data=null
     *   失败：code=404, message="记录不存在", data=null
     * </pre>
     *
     * @param id 主键 ID（从 URL 路径 {@code {id}} 自动注入）
     * @return {@code Result<Void>} — code=200 删除成功 / code=404 记录不存在
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @DeleteMapping("/bid-winning-supplier-detail/{id}")
    public Result<Void> deleteBidWinningSupplierDetail(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 列表查询中标结果明细（分页）
     *
     * <p>执行链路：</p>
     * <pre>
     *   GET /bid-winning-supplier-detail?pageNum=1&amp;pageSize=10&amp;sort=detail_id desc
     *     → PageHelper.startPage(pageNum, pageSize, sort)   ← ThreadLocal 写入
     *     → service.listAll() → mapper.selectAll()         ← 自动追加 LIMIT
     *     → PageInfo 包装（含总数/页数/当前页数据）
     *     → 包装为 Result.success(pageInfo, "查询成功")
     *     → finally { PageHelper.clearPage() }               ← 关键：清理 ThreadLocal
     * </pre>
     *
     * <p><b>sort 参数格式</b>：PageHelper 原生格式（空格分隔），例 {@code "detail_id desc"}（非逗号）</p>
     *
     * @param pageNum 页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort 排序字段（默认 {@code "detail_id desc"}，空格分隔）
     * @return {@code Result<PageInfo<BidWinningSupplierDetail>>}（code=200 / code=400）
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @GetMapping("/bid-winning-supplier-detail")
    public Result<PageInfo<BidWinningSupplierDetail>> listBidWinningSupplierDetail(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "detail_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(pageNum, pageSize, sort);
            List<BidWinningSupplierDetail> list = service.listAll();
            PageInfo<BidWinningSupplierDetail> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 批量删除中标结果明细（按主键 ID 列表）— p3 升级版
     *
     * <p>执行链路：</p>
     * <pre>
     *   POST /bid-winning-supplier-detail/batch-delete（Content-Type: application/json）
     *     → @Valid 触发 @NotEmpty 校验（ids 不能为空，max=500）
     *     → service.deleteByIds(ids)
     *       → mapper.selectByIds(ids)         ← 知道哪些 ID 真的存在
     *       → mapper.deleteByIds(existingIds) ← 真删
     *     → 返回删掉的实体列表
     *     → 包装为 Result&lt;List&lt;BidWinningSupplierDetail&gt;&gt;
     * </pre>
     *
     * <p><b>p3 升级版说明</b>：</p>
     * <ul>
     *   <li>永远 200，<b>不</b>区分"全部不存在"与"部分不存在"</li>
     *   <li>{@code data} = 实际删除的实体列表</li>
     *   <li><b>多 1 次 SELECT</b>（PK 索引，毫秒级）</li>
     *   <li><b>不</b>做完整性校验 / <b>不</b>做级联 / <b>不</b>加额外事务（Service 已有 @Transactional）</li>
     * </ul>
     *
     * @param request 批量删除请求（{@code ids} 非空，1-500 条）
     * @return {@code Result<List<BidWinningSupplierDetail>>} — code=200，data=实际删除的实体列表
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @PostMapping("/bid-winning-supplier-detail/batch-delete")
    public Result<List<BidWinningSupplierDetail>> batchDeleteBidWinningSupplierDetail(
            @Valid @RequestBody BidWinningSupplierDetailBatchDeleteRequest request) {
        List<BidWinningSupplierDetail> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }
}
