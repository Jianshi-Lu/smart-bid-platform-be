package io.gitee.lujianshi.bidwinningbackend.controller.bidwinningnotification;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.gitee.lujianshi.bidwinningbackend.annotation.SendEmailOnSuccess;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationBatchDeleteRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationCreateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationUpdateRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Diff;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import io.gitee.lujianshi.bidwinningbackend.service.bidwinningnotification.BidWinningNotificationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定标通知 Controller 层 — HTTP 入口
 *
 * <h3>链路位置（前端请求视角，从前到后）</h3>
 * <pre>
 *   前端 POST/PATCH/DELETE/GET
 *     ↓ Jackson 反序列化
 *   DTO (BidWinningNotificationCreateRequest / UpdateRequest)
 *     ↓ 字段拷贝
 *   Controller（本类，5 个端点）
 *     ↓ Service 调用
 *   Service (BidWinningNotificationService — 纯 mapper 转发)
 *     ↓ Mapper 调用
 *   Mapper (BidWinningNotificationMapper)
 *     ↓ XML 映射
 *   SQL (BidWinningNotificationMapper.xml)
 *     ↓ JDBC
 *   MySQL bid_winning_notification 表
 * </pre>
 *
 * <h3>链路位置（后端开发视角，从下到上）</h3>
 * <pre>
 *   ① bid_winning_notification 表
 *      ↓
 *   ② BidWinningNotification Entity（含 @TableName / @TableId / @NotBlank / @NotNull）
 *      ↓
 *   ③ BidWinningNotificationMapper 接口
 *      ↓
 *   ④ BidWinningNotificationMapper.xml SQL（含动态 SQL 动态 SET）
 *      ↓
 *   ⑤ BidWinningNotificationService（纯 mapper 转发）
 *      ↓
 *   ⑥ BidWinningNotificationCreateRequest / UpdateRequest DTO
 *      ↓
 *   ⑦ 本 Controller
 * </pre>
 *
 * <h3>响应格式</h3>
 * <p>所有端点统一返回 {@link Result} 包装：</p>
 * <ul>
 *   <li>{@code code} — 业务状态码（200 成功，404 资源不存在，400 参数错误，其他按需扩展）</li>
 *   <li>{@code message} — 文字消息</li>
 *   <li>{@code data} — 业务数据（删除类操作时为 null；update 端点时为 Diff&lt;T&gt;）</li>
 * </ul>
 *
 * <h3>已实现端点（6 个）</h3>
 * <ul>
 *   <li>{@code POST   /create-bid-winning-notification}        — 创建定标通知</li>
 *   <li>{@code GET    /bid-winning-notification/{id}}        — 按主键单条查询</li>
 *   <li>{@code PATCH  /bid-winning-notification/{id}}        — 按主键部分更新（返回 before/after diff）</li>
 *   <li>{@code DELETE /bid-winning-notification/{id}}        — 按主键删除</li>
 *   <li>{@code GET    /bid-winning-notification}            — 全表列表查询（分页）</li>
 *   <li>{@code POST   /bid-winning-notification/batch-delete} — 按主键 ID 列表批量删除（p3 最小实现）</li>
 * </ul>
 *
 * <h3>分页机制</h3>
 * <p>列表查询用 <b>PageHelper</b>（{@code com.github.pagehelper}）实现：</p>
 * <ul>
 *   <li>请求参数：{@code pageNum}（默认 1）、{@code pageSize}（默认 10）、{@code sort}（默认 {@code "notification_id desc"}）</li>
 *   <li>SQL 拦截：PageHelper 用 ThreadLocal 在 startPage 后拦截下一条 SELECT，自动追加 LIMIT</li>
 *   <li>ThreadLocal 清理：{@code finally { PageHelper.clearPage() }} 防止线程池污染</li>
 *   <li>异常处理：catch 任何异常返 {@code Result.error(400, "分页查询参数错误")}（不暴露 e.getMessage()）</li>
 * </ul>
 *
 * <h3>待办</h3>
 * <ul>
 *   <li>当前 {@code createBidWinningNotification} / {@code updateBidWinningNotification} 未加 {@code @Valid}，DTO 上校验注解暂未生效（待 GlobalExceptionHandler 一起补全）</li>
 *   <li>分页 sort 参数未来可加白名单（防 SQL 注入 / 防字段拼错），当前透传给 PageHelper（依赖其解析）</li>
 *   <li>统一异常处理（{@code GlobalExceptionHandler} 尚未实现）</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification.BidWinningNotificationUpdateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Diff
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Result
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification
 * @see io.gitee.lujianshi.bidwinningbackend.service.bidwinningnotification.BidWinningNotificationService
 * @see io.gitee.lujianshi.bidwinningbackend.mapper.BidWinningNotificationMapper
 */
@RestController
public class BidWinningNotificationController {

    @Resource
    private BidWinningNotificationService service;

    /**
     * 创建定标通知
     *
     * <p>执行链路：</p>
     * <pre>
     *   POST /create-bid-winning-notification（Content-Type: application/json）
     *     → Jackson 反序列化 → BidWinningNotificationCreateRequest（3 必填 + 14 可选）
     *     → 字段拷贝到 Entity
     *     → service.create(entity) → mapper.insert(entity) → SQL INSERT
     *     → MySQL 自增回填 notificationId
     *     → 包装为 Result.success(entity, "创建成功")
     * </pre>
     *
     * @param request 17 字段（3 @NotBlank/@NotNull 必填 + 14 可选）
     * @return {@code Result<BidWinningNotification>}（code=200，含自增 notificationId 的 Entity）
     */
    // 业务操作成功后通过 AOP 切面（CrudResultEmailAspect）异步发送邮件通知
    @SendEmailOnSuccess(to = "ops@company.com")
    @PostMapping("/create-bid-winning-notification")
    @Transactional(rollbackFor = Exception.class)
    public Result<BidWinningNotification> createBidWinningNotification(
            @RequestBody BidWinningNotificationCreateRequest request) {
        BidWinningNotification entity = new BidWinningNotification();
        // 3 必填字段
        entity.setHandlerId(request.getHandlerId());
        entity.setProjectNo(request.getProjectNo());
        entity.setPurchaseTaskNo(request.getPurchaseTaskNo());
        // 14 可选字段
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

        service.create(entity);
        return Result.success(entity, "创建成功");
    }

    /**
     * 按主键 ID 查询单条定标通知
     *
     * <p>执行链路：</p>
     * <pre>
     *   GET /bid-winning-notification/{id}
     *     → @PathVariable 注入 id
     *     → service.getById(id) → mapper.selectById(id) → SQL SELECT
     *     → entity 为 null → Result.error(404, "记录不存在")
     *     → entity 不为 null → Result.success(entity, "查询成功")
     * </pre>
     *
     * @param id 主键 ID（@PathVariable 注入）
     * @return {@code Result<BidWinningNotification>}（code=200 / code=404）
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @GetMapping("/bid-winning-notification/{id}")
    public Result<BidWinningNotification> getByIdBidWinningNotification(@PathVariable Integer id) {
        BidWinningNotification entity = service.getById(id);
        return entity != null
                ? Result.success(entity, "查询成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 按主键 ID 部分更新定标通知（PATCH 语义）
     *
     * <p>执行链路：</p>
     * <pre>
     *   PATCH /bid-winning-notification/{id}（Content-Type: application/json）
     *     → @PathVariable 注入 id
     *     → Jackson 反序列化 → BidWinningNotificationUpdateRequest（17 字段，全部可选）
     *     → service.getById(id) 取原始 entity（diff.before 来源）
     *     → 构造新 entity（id 来自 path，17 字段来自 body，null 字段保持 null）
     *     → service.update(newEntity) → mapper.updateById → 动态 SQL（仅更新非 null 字段）
     *     → 包装为 Result.success(new Diff(before, after), "更新成功")
     * </pre>
     *
     * <p><b>PATCH 语义</b>（RFC 5789）：</p>
     * <ul>
     *   <li>调用方传哪些字段就更新哪些字段</li>
     *   <li>未传的字段（null）在 DB 中保持原值</li>
     *   <li>XML 动态 SQL（{@code <set> + <if test="xxx != null">}）实现</li>
     * </ul>
     *
     * @param id 主键 ID（@PathVariable 注入）
     * @param request 17 字段（PATCH 可选，null=不修改）
     * @return {@code Result<Diff<BidWinningNotification>>}（code=200 含 before/after / code=404）
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @PatchMapping("/bid-winning-notification/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Diff<BidWinningNotification>> patchBidWinningNotification(
            @PathVariable Integer id,
            @RequestBody BidWinningNotificationUpdateRequest request) {
        // 1. 查原始 entity（diff.before 来源）
        BidWinningNotification before = service.getById(id);
        if (before == null) {
            return Result.error(404, "记录不存在");
        }

        // 2. 构造新 entity（id 来自 path + 17 字段来自 body，null 字段不更新）
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

        // 3. 执行更新（动态 SQL 只更新非 null 字段）
        int rows = service.update(after);
        if (rows == 0) {
            return Result.error(404, "记录不存在");
        }

        // 4. 包装 diff 返回
        return Result.success(new Diff<>(before, after), "更新成功");
    }

    /**
     * 按主键 ID 删除定标通知
     *
     * <p>执行链路：</p>
     * <pre>
     *   DELETE /bid-winning-notification/{id}
     *     → @PathVariable 注入 id
     *     → service.delete(id) → mapper.deleteById(id) → SQL DELETE
     *     → rows = 0 → Result.error(404, "记录不存在")
     *     → rows = 1 → Result.success(null, "删除成功")
     * </pre>
     *
     * @param id 主键 ID（@PathVariable 注入）
     * @return {@code Result<Void>}（code=200 / code=404，data 始终为 null）
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @DeleteMapping("/bid-winning-notification/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteBidWinningNotification(@PathVariable Integer id) {
        int rows = service.delete(id);
        return rows > 0
                ? Result.success(null, "删除成功")
                : Result.error(404, "记录不存在");
    }

    /**
     * 批量删除定标通知（按主键 ID 列表）
     *
     * <p>执行链路：</p>
     * <pre>
     *   POST /bid-winning-notification/batch-delete（Content-Type: application/json）
     *     → @Valid 触发 @NotEmpty 校验（ids 不能为空，max=500）
     *     → service.deleteByIds(ids)
     *       → mapper.selectByIds(ids)         ← 知道哪些 ID 真的存在
     *       → mapper.deleteByIds(existingIds) ← 真删
     *     → 返回删掉的实体列表
     *     → 包装为 Result&lt;List&lt;BidWinningNotification&gt;&gt;
     * </pre>
     *
     * <p><b>设计：升级 p3（返删掉的实体列表）</b>：</p>
     * <ul>
     *   <li>永远 200，<b>不</b>区分"全部不存在"与"部分不存在"</li>
     *   <li>{@code data} = 实际删除的实体列表（前端展示用，无需自己再查"哪些存在"）</li>
     *   <li>不存在的 ID：不在 data 中，前端 diff 自己请求的 ids 即可知道 missing</li>
     *   <li><b>多 1 次 SELECT</b>（PK 索引，毫秒级），换 response payload 实用性</li>
     *   <li><b>不</b>做完整性校验 / <b>不</b>做级联 / <b>不</b>加事务</li>
     *   <li><b>不</b>做孤儿清理：{@code bid_winning_supplier_detail} 中引用本表 {@code notification_id} 的行会变孤儿</li>
     *   <li>DB 物理顺序（无 ORDER BY），最小实现</li>
     * </ul>
     *
     * @param request 批量删除请求（{@code ids} 非空，1-500 条）
     * @return {@code Result<List<BidWinningNotification>>} — code=200，data=实际删除的实体列表
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @PostMapping("/bid-winning-notification/batch-delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<BidWinningNotification>> batchDeleteBidWinningNotification(@Valid @RequestBody BidWinningNotificationBatchDeleteRequest request) {
        List<BidWinningNotification> deleted = service.deleteByIds(request.getIds());
        return Result.success(deleted, "批量删除完成");
    }

    /**
     * 列表查询定标通知（分页）
     *
     * <p>执行链路：</p>
     * <pre>
     *   GET /bid-winning-notification?pageNum=1&amp;pageSize=10&amp;sort=notification_id,desc
     *     → PageHelper.startPage(pageNum, pageSize, sort)   ← ThreadLocal 写入
     *     → service.listAll() → mapper.selectAll()         ← 自动追加 LIMIT
     *     → PageInfo 包装（含总数/页数/当前页数据）
     *     → 包装为 Result.success(pageInfo, "查询成功")
     *     → finally { PageHelper.clearPage() }               ← 关键：清理 ThreadLocal
     * </pre>
     *
     * <p><b>sort 参数格式</b>：PageHelper 原生格式（空格分隔），例 {@code "notification_id desc"}（非逗号）</p>
     *
     * @param pageNum 页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     * @param sort 排序字段（默认 {@code "notification_id desc"}，空格分隔）
     * @return {@code Result<PageInfo<BidWinningNotification>>}（code=200 / code=400）
     */
    @SendEmailOnSuccess(to = "ops@company.com")
    @GetMapping("/bid-winning-notification")
    public Result<PageInfo<BidWinningNotification>> listBidWinningNotification(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "notification_id desc") String sort) {
        try {
            //noinspection try
            PageHelper.startPage(pageNum, pageSize, sort);
            List<BidWinningNotification> list = service.listAll();
            PageInfo<BidWinningNotification> pageInfo = new PageInfo<>(list);
            return Result.success(pageInfo, "查询成功");
        } catch (Exception e) {
            return Result.error(400, "分页查询参数错误");
        } finally {
            PageHelper.clearPage();
        }
    }
}
