package io.gitee.lujianshi.bidwinningbackend.dto.bidwinningnotification;

import io.gitee.lujianshi.bidwinningbackend.controller.bidwinningnotification.BidWinningNotificationController;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除定标通知 — 请求 DTO（独立于其他实体的批量删除 DTO）
 *
 * <h3>设计原则：每端点独立 DTO</h3>
 * <ul>
 *   <li>本 DTO <b>专属于</b> {@code POST /bid-winning-notification/batch-delete}，不复用于其他端点</li>
 *   <li>理由：避免端点间耦合（改一个端点的 DTO 不应影响其他端点），符合阿里《Java 开发手册》"推荐每 API 入口对应独立 DTO"</li>
 *   <li>命名规范：{@code <EntityName>BatchDeleteRequest}，实体名 + "BatchDelete" + "Request"</li>
 *   <li>与 {@code BidHandlerInfoBatchDeleteRequest} 等结构完全相同，但<b>类型独立</b></li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>必填</th><th>校验</th><th>说明</th></tr>
 *   <tr><td>ids</td><td>List&lt;Integer&gt;</td><td>✅</td><td>@NotEmpty + @Size(max=500)</td><td>待删除的定标通知主键 ID 列表（notification_id）</td></tr>
 * </table>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code @NotEmpty}：ids 列表不能为 null 或空（{@code @NotEmpty} 同时校验 null 和 empty）</li>
 *   <li>{@code @Size(max=500)}：单次最多 500 条，防止大批量误操作与长 SQL 锁表</li>
 * </ul>
 *
 * <h3>响应语义（p3 最小实现）</h3>
 * <ul>
 *   <li>Controller 返 {@code Result<Integer>}，{@code data} = 实际删除条数（受 SQL 实际影响行数）</li>
 *   <li>始终 200，<b>不</b>区分"全部不存在"与"部分不存在"（前端根据自己请求的 ids 自行 diff）</li>
 *   <li>本 DTO 是 <b>最小实现</b>版本，未引入 {@code PartialResult<T>}（避免额外 SELECT 查询）</li>
 * </ul>
 *
 * <h3>p3 最小实现的逻辑外键风险</h3>
 * <ul>
 *   <li>{@code bid_winning_supplier_detail.notification_id} 是<b>逻辑外键</b>（DB 无 FK 约束）</li>
 *   <li>删除本表后，明细行会变<b>孤儿</b>（引用已不存在的 notification_id）</li>
 *   <li>本最小实现 <b>不</b>做级联删除 / <b>不</b>做孤儿清理，由应用层负责（未来可加 Service 跨 Mapper 编排）</li>
 * </ul>
 *
 * <h3>调用示例</h3>
 * <pre>
 *   POST /bid-winning-notification/batch-delete
 *   Content-Type: application/json
 *
 *   { "ids": [1, 2, 3] }
 * </pre>
 *
 * <h3>响应示例</h3>
 * <pre>
 *   { "code": 200, "message": "批量删除完成", "data": 2 }
 * </pre>
 *
 * <h3>链路位置</h3>
 * <pre>
 *   ① Controller 接收 BidWinningNotificationBatchDeleteRequest（@Valid 触发 @NotEmpty 校验）
 *      ↓
 *   ② 字段拷贝：ids → service.deleteByIds(ids)
 *      ↓
 *   ③ Service → Mapper.deleteByIds（XML 动态 SQL: WHERE id IN (foreach)）
 *      ↓
 *   ④ 返回影响行数 → Result&lt;Integer&gt;
 * </pre>
 *
 * @see BidWinningNotificationController
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Result
 */
@Data
public class BidWinningNotificationBatchDeleteRequest {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 500, message = "单次批量删除不能超过 500 条")
    private List<Integer> ids;
}
