package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。统一返回 {@link Result} 格式：
 * {@code @Valid} 校验失败 → 400 + 第一个错误消息；
 * {@link IllegalArgumentException} → 400 + 异常消息；
 * 其他异常 → 500 兜底（不泄漏堆栈）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 {@code @Valid} 校验失败。取第一个校验错误消息返回。
     *
     * @param e 校验异常
     * @return 400 + 第一个错误消息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.error(400, msg);
    }

    /**
     * 处理业务参数异常。
     *
     * @param e 非法参数异常
     * @return 400 + 异常消息
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    /**
     * 处理未捕获异常（兜底）。不泄漏堆栈信息。
     *
     * @param e 异常
     * @return 500 + 固定消息
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        return Result.error(500, "服务器内部错误");
    }
}
