package io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail;

import io.gitee.lujianshi.bidwinningbackend.controller.BidWinningSupplierDetailController;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除中标结果明细 — 请求 DTO（独立于其他实体的批量删除 DTO，<b>预留</b>）
 *
 * <h3>当前状态</h3>
 * <ul>
 *   <li>本 DTO 已创建，<b>但</b> {@code BidWinningSupplierDetailController} 尚未实现（当前是 4 行空 stub）</li>
 *   <li>当 {@code POST /bid-winning-supplier-detail/batch-delete} 端点实现时，本 DTO 直接复用，无需新建</li>
 *   <li>命名规范：{@code <EntityName>BatchDeleteRequest}，与已实现的 2 个批量删除 DTO 保持一致</li>
 * </ul>
 *
 * <h3>设计原则：每端点独立 DTO</h3>
 * <ul>
 *   <li>本 DTO <b>专属于</b>未来的 {@code POST /bid-winning-supplier-detail/batch-delete}，不复用于其他端点</li>
 *   <li>理由：避免端点间耦合（改一个端点的 DTO 不应影响其他端点），符合阿里《Java 开发手册》"推荐每 API 入口对应独立 DTO"</li>
 *   <li>与 {@code DeleteRequest} / {@code BidWinningNotificationBatchDeleteRequest} 结构完全相同，但<b>类型独立</b></li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>必填</th><th>校验</th><th>说明</th></tr>
 *   <tr><td>ids</td><td>List&lt;Integer&gt;</td><td>✅</td><td>@NotEmpty + @Size(max=500)</td><td>待删除的中标结果明细主键 ID 列表（detail_id）</td></tr>
 * </table>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code @NotEmpty}：ids 列表不能为 null 或空</li>
 *   <li>{@code @Size(max=500)}：单次最多 500 条，防止大批量误操作与长 SQL 锁表</li>
 * </ul>
 *
 * <h3>未来响应语义（p3 最小实现）</h3>
 * <ul>
 *   <li>Controller 返 {@code Result<Integer>}，{@code data} = 实际删除条数</li>
 *   <li>始终 200，<b>不</b>区分"全部不存在"与"部分不存在"（前端根据自己请求的 ids 自行 diff）</li>
 * </ul>
 *
 * <h3>未来调用示例</h3>
 * <pre>
 *   POST /bid-winning-supplier-detail/batch-delete
 *   Content-Type: application/json
 *
 *   { "ids": [1, 2, 3] }
 * </pre>
 *
 * @see BidWinningSupplierDetailController
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Result
 */
@Data
@SuppressWarnings("unused")
public class BidWinningSupplierDetailBatchDeleteRequest {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 500, message = "单次批量删除不能超过 500 条")
    private List<Integer> ids;
}
