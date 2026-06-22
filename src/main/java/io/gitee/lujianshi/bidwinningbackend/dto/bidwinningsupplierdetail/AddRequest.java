package io.gitee.lujianshi.bidwinningbackend.dto.bidwinningsupplierdetail;

import io.gitee.lujianshi.bidwinningbackend.controller.BidWinningSupplierDetailController;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建中标结果明细 — 请求 DTO（Data Transfer Object）
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表（bid_winning_supplier_detail，V5 建表）
 *      ↓
 *   ② Entity 层（BidWinningSupplierDetail）
 *      ↓
 *   ③ Mapper 接口
 *      ↓
 *   ④ Mapper XML
 *      ↓
 *   ⑤ Service
 *      ↓
 *   ⑥ DTO 层（本类）— HTTP 请求载体 + 校验注解
 *      ↓
 *   ⑦ Controller
 * </pre>
 *
 * <h3>字段 ↔ Entity 对应（3 字段，排除 detailId / createdAt）</h3>
 *
 * <h3>必填字段（3 个，对应 DB 层 NOT NULL）</h3>
 * <ul>
 *   <li>{@code notificationId} — {@code @NotNull} Integer，逻辑外键（应用层维护）关联 {@code bid_winning_notification.notification_id}</li>
 *   <li>{@code supplierName} — {@code @NotBlank} + {@code @Size(max=100)} String</li>
 *   <li>{@code amount} — {@code @NotNull} BigDecimal，中标价（元）</li>
 * </ul>
 *
 * <h3>校验触发</h3>
 * Controller 方法参数加 {@code @Valid} 后生效；当前 {@code createBidWinningSupplierDetail} 未加（待 GlobalExceptionHandler 一起补全）。
 *
 * <h3>与 {@code UpdateRequest} 的对比</h3>
 * <table border="1">
 *   <tr><th>维度</th><th>CreateRequest（本类）</th><th>UpdateRequest</th></tr>
 *   <tr><td>HTTP 方法</td><td>POST</td><td>PATCH</td></tr>
 *   <tr><td>字段集合</td><td>3 字段</td><td>3 字段（完全相同）</td></tr>
 *   <tr><td>detailId</td><td>不在 DTO（DB 自增）</td><td>不在 DTO（URL path @PathVariable 传入）</td></tr>
 *   <tr><td>校验规则</td><td>{@code @NotNull} + {@code @NotBlank} + {@code @Size}</td><td>仅 {@code @Size}（PATCH 语义下 null 是合法值）</td></tr>
 * </table>
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
 * @see UpdateRequest
 * @see BidWinningSupplierDetailController
 */
@Data
public class AddRequest {

    @NotNull(message = "定标通知ID不能为空")
    private Integer notificationId;

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 100, message = "供应商名称长度不能超过100")
    private String supplierName;

    @NotNull(message = "中标价不能为空")
    private BigDecimal amount;

    @NotBlank(message = "通知邮件收件人不能为空")
    @Size(max = 255, message = "通知邮件收件人长度不能超过255")
    private String targetEmail;
}
