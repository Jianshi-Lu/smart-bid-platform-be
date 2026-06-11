package io.gitee.lujianshi.bidwinningbackend.dto;

import jakarta.validation.constraints.NotBlank;
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
 *   ④ Mapper XML
 *      ↓
 *   ⑤ Service
 *      ↓
 *   ⑥ DTO（本类）— HTTP 请求载体
 *      ↓
 *   ⑦ Controller 把本 DTO + URL path id 转换为 Entity 传给 Service
 * </pre>
 *
 * <h3>与 {@code BidHandlerInfoCreateRequest} 的对比</h3>
 * <table border="1">
 *   <tr><th>维度</th><th>CreateRequest</th><th>UpdateRequest（本类）</th></tr>
 *   <tr><td>字段集合</td><td>3 字段</td><td>3 字段（<b>完全相同</b>）</td></tr>
 *   <tr><td>handlerId</td><td>不在 DTO（DB 自增）</td><td>不在 DTO（<b>URL path @PathVariable 传入</b>）</td></tr>
 *   <tr><td>校验规则</td><td>{@code @NotBlank} + {@code @Size}</td><td>同左</td></tr>
 *   <tr><td>未来扩展点</td><td>—</td><td>部分更新 / 乐观锁版本号 / 审计字段</td></tr>
 * </table>
 *
 * <h3>校验机制</h3>
 * <ul>
 *   <li>{@link NotBlank}：字符串非 null 且去除首尾空格后长度 &gt; 0</li>
 *   <li>{@link Size}：字符串最大长度限制</li>
 *   <li>校验触发：Controller 方法参数加 {@code @Valid} 注解</li>
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
 * @see io.gitee.lujianshi.bidwinningbackend.dto.BidHandlerInfoCreateRequest
 * @see io.gitee.lujianshi.bidwinningbackend.dto.Diff
 * @see io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController
 */
@Data
public class BidHandlerInfoUpdateRequest {

    @NotBlank(message = "经办人姓名不能为空")
    @Size(max = 50, message = "经办人姓名长度不能超过50")
    private String handlerName;

    @NotBlank(message = "所属公司不能为空")
    @Size(max = 100, message = "公司名称长度不能超过100")
    private String companyName;

    @NotBlank(message = "所属部门不能为空")
    @Size(max = 100, message = "部门名称长度不能超过100")
    private String departmentName;
}
