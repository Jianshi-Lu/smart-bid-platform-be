package io.gitee.lujianshi.bidwinningbackend.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import lombok.Data;

import java.util.Map;

/**
 * 统一响应包装。所有 Controller 端点返回 {@code Result<T>}。
 *
 * <p>HTTP 状态码由 Web 层响应状态承载，{@code code} 用于承载稳定的成功码或模块化业务错误码。
 * 文字消息放 {@code message}，业务数据放 {@code data}（删除类操作时为 null）。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 请求或业务操作是否成功。前端优先使用该字段判断结果。 */
    private boolean success;

    /** 成功时为 SUCCESS，失败时为模块化错误码。 */
    private String code;

    private String message;
    private T data;

    /** 产生响应的服务名。由应用配置或全局异常处理器填充。 */
    private String service;

    /** 产生问题的业务模块。 */
    private String module;

    /** 当前业务操作。 */
    private String operation;

    /** 当前操作阶段。 */
    private String stage;

    /** 当前错误是否适合重试。 */
    private Boolean retryable;

    /** 用于串联一次请求链路的追踪编号。 */
    private String traceId;

    /** 用于定位一次具体错误的编号。 */
    private String errorId;

    /** 供日志排查使用的结构化上下文。 */
    private Map<String, Object> details;

    /**
     * 成功响应。
     *
     * @param data    业务数据
     * @param message 成功消息
     */
    public static <T> Result<T> success(T data, String message) {
        Result<T> r = new Result<>();
        r.setSuccess(true);
        r.setCode("SUCCESS");
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    /**
     * 根据结构化错误码创建失败响应。
     *
     * @param errorCode 错误码
     * @return 失败响应
     */
    public static <T> Result<T> failure(AppErrorCode errorCode) {
        Result<T> r = new Result<>();
        r.setSuccess(false);
        r.setCode(errorCode.getCode());
        r.setMessage(errorCode.getDefaultMessage());
        r.setRetryable(errorCode.isRetryable());
        return r;
    }

    /**
     * 根据结构化错误码和公开消息创建失败响应。
     *
     * @param errorCode 错误码
     * @param message   对外返回的消息
     * @return 失败响应
     */
    public static <T> Result<T> failure(
            AppErrorCode errorCode,
            String message) {
        Result<T> r = failure(errorCode);
        r.setMessage(message);
        return r;
    }

    /**
     * 根据异常对象创建失败响应，供全局异常处理器使用。
     *
     * @param exception 结构化应用异常
     * @return 失败响应
     */
    public static <T> Result<T> failure(AppException exception) {
        Result<T> r = failure(exception.getErrorCode(), exception.getPublicMessage());
        r.setModule(exception.getModule());
        r.setOperation(exception.getOperation());
        r.setStage(exception.getStage());
        r.setDetails(exception.getDetails());
        return r;
    }

}
