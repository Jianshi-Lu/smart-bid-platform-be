package io.gitee.lujianshi.bidwinningbackend.dto;

import lombok.Data;

/**
 * 通用响应包装类 — 统一所有 Controller 的返回值结构
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>所有 Controller 端点统一返回 {@code Result<T>}，前端解析结构一致</li>
 *   <li>HTTP 状态码只表示传输层状态（200/400/500），<b>业务状态由 {@code code} 字段表达</b></li>
 *   <li>文字消息放 {@code message}（如"创建成功"、"记录不存在"）</li>
 *   <li>业务数据放 {@code data}（删除类操作时为 {@code null}；update 端点为 {@code Diff<T>}；列表查询为 {@code PageInfo<T>}）</li>
 * </ul>
 *
 * <h3>使用约定</h3>
 * <table border="1">
 *   <tr><th>场景</th><th>{@code code}</th><th>{@code message}</th><th>{@code data}</th></tr>
 *   <tr><td>成功（含数据）</td><td>200</td><td>自定义（如"创建成功"）</td><td>业务对象</td></tr>
 *   <tr><td>成功（无数据，如删除）</td><td>200</td><td>自定义（如"删除成功"）</td><td>{@code null}</td></tr>
 *   <tr><td>成功（更新 PATCH）</td><td>200</td><td>"更新成功"</td><td>{@code Diff<T>}</td></tr>
 *   <tr><td>成功（列表分页）</td><td>200</td><td>"查询成功"</td><td>{@code PageInfo<T>}</td></tr>
 *   <tr><td>业务失败（资源不存在）</td><td>404</td><td>必填（如"记录不存在"）</td><td>{@code null}</td></tr>
 *   <tr><td>参数错误（如分页 sort 字段拼错）</td><td>400</td><td>必填</td><td>{@code null}</td></tr>
 * </table>
 *
 * <h3>前端解析示例</h3>
 * <pre>
 *   {
 *     "code": 200,
 *     "message": "创建成功",
 *     "data": {
 *       "handlerId": 1,
 *       "handlerName": "张三",
 *       "companyName": "测试公司",
 *       "departmentName": "采购部"
 *     }
 *   }
 * </pre>
 *
 * <h3>调用示例</h3>
 * <pre>
 *   // Controller 中使用
 *   return Result.success(entity, "创建成功");
 *   return Result.error(404, "记录不存在");
 *   return Result.success(new Diff&lt;&gt;(before, after), "更新成功");
 *   return Result.success(pageInfo, "查询成功");
 * </pre>
 *
 * <h3>链路位置</h3>
 * <pre>
 *   Service（业务结果）→ Controller（包装为 Result）→ Jackson（序列化为 JSON）→ 前端
 * </pre>
 *
 * <h3>当前使用范围</h3>
 * <ul>
 *   <li>{@code BidHandlerInfoController}（5 端点）</li>
 *   <li>{@code BidWinningNotificationController}（5 端点）</li>
 *   <li>未来其他 Controller 应统一使用本类</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.dto.Diff
 * @see io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController
 * @see io.gitee.lujianshi.bidwinningbackend.controller.BidWinningNotificationController
 */
@Data
public class Result<T> {

    /**
     * 业务状态码
     * <ul>
     *   <li>200 = 成功</li>
     *   <li>400 = 请求参数错误（如分页 sort 字段拼错、必填字段缺失）</li>
     *   <li>404 = 资源不存在</li>
     *   <li>500 = 服务器内部错误（待 GlobalExceptionHandler 统一处理）</li>
 *   <li>其他业务码按需扩展</li>
 * </ul>
 */
    private Integer code;

    /**
     * 文字消息（用户友好提示或错误描述）
     */
    private String message;

    /**
     * 业务数据载荷
     * <ul>
     *   <li>创建/查询：业务对象</li>
     *   <li>更新 PATCH：{@code Diff<T>}</li>
     *   <li>列表查询：{@code PageInfo<T>}</li>
     *   <li>删除类操作：{@code null}</li>
     * </ul>
     */
    private T data;

    /**
     * 成功响应（含数据）
     *
     * <p>典型用法：</p>
     * <pre>
     *   return Result.success(entity, "创建成功");
     *   return Result.success(new Diff&lt;&gt;(before, after), "更新成功");
     *   return Result.success(pageInfo, "查询成功");
     * </pre>
     *
     * @param data    业务数据
     * @param message 成功消息
     * @param <T>     数据类型
     * @return 包装好的 Result 实例（code=200）
     */
    public static <T> Result<T> success(T data, String message) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    /**
     * 业务失败响应
     *
     * <p>典型用法：</p>
     * <pre>
     *   return Result.error(404, "记录不存在");
     *   return Result.error(400, "分页查询参数错误");
     *   return Result.error(500, "服务器内部错误");
     * </pre>
     *
     * @param code    业务错误码（如 400 / 404 / 500）
     * @param message 错误描述
     * @param <T>     数据类型（通常填 {@code Void}，data 留 null）
     * @return 包装好的 Result 实例
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
