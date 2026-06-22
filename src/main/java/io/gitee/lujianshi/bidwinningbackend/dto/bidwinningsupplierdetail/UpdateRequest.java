package io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail;

import io.gitee.lujianshi.bidwinningbackend.controller.BidWinningSupplierDetailController;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新中标结果明细 — 请求 DTO（Data Transfer Object）
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表
 *      ↓
 *   ② Entity（BidWinningSupplierDetail）— 持久化映射
 *      ↓
 *   ③ Mapper 接口
 *      ↓
 *   ④ Mapper XML（含 PATCH 动态 SQL）
 *      ↓
 *   ⑤ Service
 *      ↓
 *   ⑥ DTO（本类）— HTTP 请求载体
 *      ↓
 *   ⑦ Controller 把本 DTO + URL path id 转换为 Entity 传给 Service
 * </pre>
 *
 * <h3>PATCH 语义（RFC 5789）</h3>
 * <ul>
 *   <li>本 DTO 用于 <b>PATCH</b>（部分更新），不是 PUT（全量替换）</li>
 *   <li><b>null 字段表示"不修改"</b>（与"置空"无法区分 — 是 PATCH 标准的限制）</li>
 *   <li>需要"置空"字段的客户端场景当前<b>不支持</b></li>
 *   <li>调用方传哪些字段，就更新哪些字段；未传的字段在 DB 中保持原值</li>
 * </ul>
 *
 * <h3>与 {@code AddRequest} 的对比</h3>
 * <table border="1">
 *   <tr><th>维度</th><th>CreateRequest</th><th>UpdateRequest（本类）</th></tr>
 *   <tr><td>字段集合</td><td>3 字段</td><td>3 字段（<b>完全相同</b>）</td></tr>
 *   <tr><td>detailId</td><td>不在 DTO（DB 自增）</td><td>不在 DTO（<b>URL path @PathVariable 传入</b>）</td></tr>
 *   <tr><td>校验规则</td><td>{@code @NotNull} + {@code @NotBlank} + {@code @Size}</td><td>仅 {@code @Size}（无 {@code @NotBlank}/{@code @NotNull}，PATCH 语义下 null 是合法值）</td></tr>
 *   <tr><td>HTTP 方法</td><td>POST</td><td>PATCH</td></tr>
 * </table>
 *
 * <h3>校验机制</h3>
 * <ul>
 *   <li><b>无</b> {@code @NotBlank} / {@code @NotNull}：PATCH 语义下 null 是合法值（表示"不修改"）</li>
 *   <li>保留 {@code @Size(max=100)} 限制（确保即使是 PATCH，提供值时也不超长）</li>
 *   <li>DB 层的 NOT NULL 约束是最后防线（即使 DTO 漏校验，DB 也会拒）</li>
 *   <li>校验触发：Controller 方法参数加 {@code @Valid} 注解（已补全 GlobalExceptionHandler）</li>
 * </ul>
 *
 * <h3>XML 配合（动态 SQL）</h3>
 * Mapper XML 的 {@code updateById} 用 {@code <set> + <if test="xxx != null">} 模式：
 * <pre>
 *   UPDATE bid_winning_supplier_detail
 *   &lt;set&gt;
 *     &lt;if test="notificationId != null"&gt;notification_id = #{notificationId},&lt;/if&gt;
 *     &lt;if test="supplierName != null"&gt;supplier_name = #{supplierName},&lt;/if&gt;
 *     &lt;if test="amount != null"&gt;amount = #{amount},&lt;/if&gt;
 *   &lt;/set&gt;
 *   WHERE detail_id = #{detailId}
 * </pre>
 *
 * <h3>字段 ↔ Entity 对应</h3>
 * <table border="1">
 *   <tr><th>DTO 字段</th><th>Entity 字段</th><th>DB 列</th></tr>
 *   <tr><td>notificationId</td><td>notificationId</td><td>notification_id</td></tr>
 *   <tr><td>supplierName</td><td>supplierName</td><td>supplier_name</td></tr>
 *   <tr><td>amount</td><td>amount</td><td>amount</td></tr>
 * </table>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail
 * @see AddRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Diff
 * @see BidWinningSupplierDetailController
 */
@Data
public class UpdateRequest {

    private Integer notificationId;

    @Size(max = 100, message = "供应商名称长度不能超过100")
    private String supplierName;

    private BigDecimal amount;

    private String targetEmail;
}
