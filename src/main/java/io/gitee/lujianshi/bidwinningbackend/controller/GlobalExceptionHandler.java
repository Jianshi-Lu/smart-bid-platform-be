package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 — 统一捕获 Controller 层异常并返回 {@link Result} 格式。
 *
 * <h3>处理范围</h3>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — {@code @Valid} 校验失败，返回 400 + 第一个错误消息</li>
 *   <li>{@link IllegalArgumentException} — 参数非法，返回 400 + 异常消息</li>
 *   <li>{@link Exception} — 兜底，返回 500，不泄漏堆栈</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.error(400, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        return Result.error(500, "服务器内部错误");
    }
}
