package io.gitee.lujianshi.bidwinningbackend.exception;

import java.util.Map;
import java.util.Objects;

/**
 * 应用层结构化异常基类。
 *
 * <p>该异常只承载异常语义和排查上下文，不负责业务补偿，也不负责直接生成 HTTP 响应。
 * 具体业务异常可以在后续需要时继承本类。</p>
 */
public class AppException extends RuntimeException {

    private final AppErrorCode errorCode;
    private final String module;
    private final String operation;
    private final String stage;
    private final Map<String, Object> details;
    private final String publicMessage;

    public AppException(
            AppErrorCode errorCode,
            String module,
            String operation,
            String stage,
            String message,
            Map<String, Object> details,
            Throwable cause) {
        super(message == null || message.isBlank()
                ? Objects.requireNonNull(errorCode).getDefaultMessage()
                : message, cause);
        this.errorCode = Objects.requireNonNull(errorCode);
        this.module = module;
        this.operation = operation;
        this.stage = stage;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.publicMessage = message == null || message.isBlank()
                ? errorCode.getDefaultMessage()
                : message;
    }

    public AppException(
            AppErrorCode errorCode,
            String module,
            String operation,
            String stage,
            String message,
            Throwable cause) {
        this(errorCode, module, operation, stage, message, Map.of(), cause);
    }

    public AppException(
            AppErrorCode errorCode,
            String module,
            String operation,
            String stage,
            String message,
            Map<String, Object> details) {
        this(errorCode, module, operation, stage, message, details, null);
    }

    public AppException(
            AppErrorCode errorCode,
            String module,
            String operation,
            String stage,
            String message) {
        this(errorCode, module, operation, stage, message, Map.of(), null);
    }

    public AppException(AppErrorCode errorCode) {
        this(errorCode, null, null, null, null, Map.of(), null);
    }

    public AppErrorCode getErrorCode() {
        return errorCode;
    }

    public String getModule() {
        return module;
    }

    public String getOperation() {
        return operation;
    }

    public String getStage() {
        return stage;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    /**
     * 返回允许暴露给调用方的消息。
     *
     * <p>全局异常处理器应使用该方法，不应直接从底层 cause 读取消息。</p>
     *
     * @return 对外错误消息
     */
    public String getPublicMessage() {
        return publicMessage;
    }
}
