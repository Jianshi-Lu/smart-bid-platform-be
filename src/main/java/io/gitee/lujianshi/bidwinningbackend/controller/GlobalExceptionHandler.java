package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器。统一返回 {@link Result} 格式：
 * {@code @Valid} 校验失败 → 400 + 第一个错误消息；
 * 文件上传超限 → 400 + 明确的文件过大提示；
 * {@link IllegalArgumentException} → 400 + 异常消息；
 * 其他异常 → 500 兜底（不泄漏堆栈）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理单文件或整个 multipart 请求超过配置限制的异常。
     *
     * <p>该异常发生在 Spring 解析 multipart 请求阶段，此时请求尚未进入 Controller。</p>
     *
     * @param e 文件上传大小超限异常
     * @return 400 + 文件大小超限提示
     * @since 1.0
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return Result.error(400, "文件大小超过限制");
    }

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
