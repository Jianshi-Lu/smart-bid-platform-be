package io.gitee.lujianshi.bidwinningbackend.dto.common;

import lombok.Data;

/**
 * 统一响应包装。所有 Controller 端点返回 {@code Result<T>}。
 *
 * <p>业务状态码约定：200=成功，400=请求参数错误，404=资源不存在，409=资源正在处理中，500=服务器内部错误。
 * 文字消息放 {@code message}，业务数据放 {@code data}（删除类操作时为 null）。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    /**
     * 成功响应。
     *
     * @param data    业务数据
     * @param message 成功消息
     */
    public static <T> Result<T> success(T data, String message) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    /**
     * 业务失败响应。
     *
     * @param code    业务错误码
     * @param message 错误描述
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
