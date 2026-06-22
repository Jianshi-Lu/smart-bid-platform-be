package io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo;

import io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建经办人 — 请求 DTO（Data Transfer Object）
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表（bid_handler_info）
 *      ↓
 *   ② Entity（BidHandlerInfo）— 持久化映射
 *      ↓
 *   ③ Mapper 接口
 *      ↓
 *   ④ Mapper XML
 *      ↓
 *   ⑤ Service
 *      ↓
 *   ⑥ DTO（本类）— HTTP 请求载体
 *      ↓
 *   ⑦ Controller 把本 DTO 转换为 Entity 传给 Service
 * </pre>
 *
 * <h3>为什么 DTO 与 Entity 分离？</h3>
 * <ul>
 *   <li><b>职责不同</b>：DTO 是 HTTP 请求/响应载体，Entity 是数据库映射</li>
 *   <li><b>字段可变</b>：Entity 字段对应 DB 列，DTO 字段对应业务入参，演进互不干扰</li>
 *   <li><b>校验独立</b>：DTO 可加 {@code @NotBlank} / {@code @Size} 等校验，Entity 不需要</li>
 *   <li><b>安全隔离</b>：Entity 不直接暴露给前端，避免泄漏敏感字段</li>
 * </ul>
 *
 * <h3>与 {@code UpdateRequest} 的对比</h3>
 * <table border="1">
 *   <tr><th>维度</th><th>CreateRequest（本类）</th><th>UpdateRequest</th></tr>
 *   <tr><td>HTTP 方法</td><td>POST</td><td>PATCH</td></tr>
 *   <tr><td>字段集合</td><td>3 字段</td><td>3 字段（完全相同）</td></tr>
 *   <tr><td>handlerId</td><td>不在 DTO（DB 自增）</td><td>不在 DTO（URL path @PathVariable 传入）</td></tr>
 *   <tr><td>校验规则</td><td>{@code @NotBlank} + {@code @Size}</td><td>仅 {@code @Size}（PATCH 语义下 null 是合法值）</td></tr>
 * </table>
 *
 * <h3>校验机制</h3>
 * <ul>
 *   <li>{@link NotBlank}：字符串非 null 且去除首尾空格后长度 &gt; 0</li>
 *   <li>{@link Size}：字符串最大长度限制</li>
 *   <li>校验触发：Controller 方法参数加 {@code @Valid} 注解（当前未加，待 GlobalExceptionHandler 一起补全）</li>
 *   <li>校验失败：抛 {@code MethodArgumentNotValidException}（由 {@code GlobalExceptionHandler} 统一处理）</li>
 * </ul>
 *
 * <h3>字段 ↔ Entity 对应</h3>
 * <table border="1">
 *   <tr><th>DTO 字段</th><th>Entity 字段</th><th>DB 列</th></tr>
 *   <tr><td>handlerName</td><td>handlerName</td><td>handler_name</td></tr>
 *   <tr><td>companyName</td><td>companyName</td><td>company_name</td></tr>
 *   <tr><td>departmentName</td><td>departmentName</td><td>department_name</td></tr>
 * </table>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo
 * @see UpdateRequest
 * @see BidHandlerInfoController
 */
@Data
public class AddRequest {
    @NotBlank(message = "经办人姓名不能为空")
    @Size(max = 50, message = "经办人姓名长度不能超过50")
    private String handlerName;

    @NotBlank(message = "所属公司不能为空")
    @Size(max = 100, message = "公司名称长度不能超过100")
    private String companyName;

    @NotBlank(message = "所属部门不能为空")
    @Size(max = 100, message = "部门名称长度不能超过100")
    private String departmentName;

    /**
     * 通知邮件收件人。
     *
     * <p>必填。创建成功后由 {@code BidHandlerInfoService.sendOperationEmail}
     * 异步发送 HTML 通知邮件至本地址。</p>
     */
    @NotBlank(message = "通知邮件收件人不能为空")
    @Size(max = 255, message = "通知邮件收件人长度不能超过255")
    private String targetEmail;
}
