package io.gitee.lujianshi.bidwinningbackend.dto.bidhandlerinfo;

import io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新经办人 — 请求 DTO（Data Transfer Object）
 *
 * <h3>在整个开发链路中的位置</h3>
 * <pre>
 *   ① 数据库表
 *      ↓
 *   ② Entity（BidHandlerInfo）— 持久化映射
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
 *   <li>需要"置空"字段的客户端场景当前<b>不支持</b>，建议加默认值/改用其他字段表达"无"</li>
 *   <li>调用方传哪些字段，就更新哪些字段；未传的字段在 DB 中保持原值</li>
 * </ul>
 *
 * <h3>与 {@code AddRequest} 的对比</h3>
 * <table border="1">
 *   <tr><th>维度</th><th>CreateRequest</th><th>UpdateRequest（本类）</th></tr>
 *   <tr><td>字段集合</td><td>3 字段</td><td>3 字段（<b>完全相同</b>）</td></tr>
 *   <tr><td>handlerId</td><td>不在 DTO（DB 自增）</td><td>不在 DTO（<b>URL path @PathVariable 传入</b>）</td></tr>
 *   <tr><td>校验规则</td><td>{@code @NotBlank} + {@code @Size}</td><td>仅 {@code @Size}（无 {@code @NotBlank}，PATCH 语义下 null 是合法值）</td></tr>
 *   <tr><td>HTTP 方法</td><td>POST</td><td>PATCH</td></tr>
 *   <tr><td>未来扩展点</td><td>—</td><td>乐观锁版本号 / 审计字段 / expectedUpdatedAt</td></tr>
 * </table>
 *
 * <h3>校验机制</h3>
 * <ul>
 *   <li><b>无</b> {@code @NotBlank}：PATCH 语义下 null 是合法值（表示"不修改"）</li>
 *   <li>保留 {@code @Size(max=...)} 限制（确保即使是 PATCH，提供值时也不超长）</li>
 *   <li>DB 层的 NOT NULL 约束是最后防线（即使 DTO 漏校验，DB 也会拒）</li>
 *   <li>校验触发：Controller 方法参数加 {@code @Valid} 注解（当前未加，待 GlobalExceptionHandler 一起补全）</li>
 * </ul>
 *
 * <h3>XML 配合（动态 SQL）</h3>
 * Mapper XML 的 {@code updateById} 用 {@code <set> + <if test="xxx != null">} 模式：
 * <pre>
 *   UPDATE bid_handler_info
 *   &lt;set&gt;
 *     &lt;if test="handlerName != null"&gt;handler_name = #{handlerName},&lt;/if&gt;
 *     &lt;if test="companyName != null"&gt;company_name = #{companyName},&lt;/if&gt;
 *     &lt;if test="departmentName != null"&gt;department_name = #{departmentName},&lt;/if&gt;
 *   &lt;/set&gt;
 *   WHERE handler_id = #{handlerId}
 * </pre>
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
 * @see AddRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.common.Diff
 * @see BidHandlerInfoController
 */
@Data
public class UpdateRequest {

    @Size(max = 50, message = "经办人姓名长度不能超过50")
    private String handlerName;

    @Size(max = 100, message = "公司名称长度不能超过100")
    private String companyName;

    @Size(max = 100, message = "部门名称长度不能超过100")
    private String departmentName;

    private String targetEmail;
}
