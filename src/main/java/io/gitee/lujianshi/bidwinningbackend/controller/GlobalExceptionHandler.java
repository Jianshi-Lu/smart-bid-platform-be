package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;

import java.util.UUID;

/**
 * Web 层全局异常处理器。
 *
 * <p>该处理器负责将请求处理链路中未解决的异常统一转换为 HTTP 状态码和
 * {@link Result} 响应。业务补偿、事务回滚和状态修复仍由对应 Service 负责。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(
            GlobalExceptionHandler.class);

    /** 当前服务名，用于统一响应中的 service 字段。 */
    @Value("${spring.application.name:bid-winning-backend}")
    private String serviceName = "bid-winning-backend";

    // ==================== 结构化应用异常 ====================

    /** 处理结构化应用异常。 */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Result<Void>> handleAppException(AppException e) {
        Result<Void> result = Result.failure(e);
        enrich(result, e.getErrorCode());
        return response(e.getErrorCode(), result);
    }

    // ==================== 文件与业务异常 ====================

    /** 处理单文件或整个 multipart 请求超过配置限制的异常。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e) {
        return failure(AppErrorCode.COMMON_FILE_TOO_LARGE);
    }

    // ==================== 数据库异常 ====================

    /**
     * 统一处理未在业务 Service 中转换的数据库访问异常。
     *
     * <p>业务 Service 已经能够确定具体模块时，应优先抛出带模块信息的 AppException；
     * 此处负责处理剩余的通用数据库异常，并根据 Spring DAO 异常类型映射稳定错误码。</p>
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDatabaseException(
            DataAccessException e) {
        AppErrorCode errorCode = databaseErrorCodeOf(e);
        log.error("数据库访问异常: code={}, exceptionType={}",
                errorCode.getCode(), e.getClass().getName(), e);

        Result<Void> result = Result.failure(errorCode);
        result.setModule("database");
        result.setOperation("DATABASE_ACCESS");
        result.setStage("PERSISTENCE");
        enrich(result, errorCode);
        return response(errorCode, result);
    }

    /** 根据 Spring DAO 异常类型映射数据库错误码。 */
    private AppErrorCode databaseErrorCodeOf(DataAccessException e) {
        if (e instanceof DuplicateKeyException) {
            return AppErrorCode.DATABASE_DUPLICATE_KEY;
        }
        if (e instanceof DataIntegrityViolationException) {
            return AppErrorCode.DATABASE_CONSTRAINT_VIOLATION;
        }
        if (e instanceof QueryTimeoutException) {
            return AppErrorCode.DATABASE_QUERY_TIMEOUT;
        }
        if (e instanceof DataAccessResourceFailureException) {
            return AppErrorCode.DATABASE_CONNECTION_FAILED;
        }
        return AppErrorCode.DATABASE_OPERATION_FAILED;
    }

    // ==================== 请求解析与参数校验异常 ====================

    /** 处理 JSON 请求体或参数转换失败。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException e) {
        return failure(AppErrorCode.COMMON_REQUEST_PARSE_FAILED);
    }

    /** 处理请求体 DTO 的 Bean Validation 失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .orElse(AppErrorCode.COMMON_INVALID_ARGUMENT.getDefaultMessage());
        return failure(AppErrorCode.COMMON_INVALID_ARGUMENT, message);
    }

    /** 处理 multipart/form-data 模型绑定失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .orElse(AppErrorCode.COMMON_INVALID_ARGUMENT.getDefaultMessage());
        return failure(AppErrorCode.COMMON_INVALID_ARGUMENT, message);
    }

    /** 处理路径变量或请求参数类型转换失败。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return failure(AppErrorCode.COMMON_INVALID_ARGUMENT,
                "请求参数格式不正确: " + e.getName());
    }

    /** 处理缺少请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return failure(AppErrorCode.COMMON_INVALID_ARGUMENT,
                "缺少请求参数: " + e.getParameterName());
    }

    /** 处理缺少路径变量。 */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<Result<Void>> handleMissingPathVariable(
            MissingPathVariableException e) {
        return failure(AppErrorCode.COMMON_REQUEST_PARSE_FAILED);
    }

    /** 处理缺少请求头。 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(
            MissingRequestHeaderException e) {
        return failure(AppErrorCode.COMMON_INVALID_ARGUMENT,
                "缺少请求头: " + e.getHeaderName());
    }

    /** 处理缺少 multipart 文件分片。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result<Void>> handleMissingRequestPart(
            MissingServletRequestPartException e) {
        return failure(AppErrorCode.COMMON_INVALID_ARGUMENT,
                "缺少上传文件: " + e.getRequestPartName());
    }

    // ==================== 通用异常与系统兜底 ====================

    /** 处理业务层抛出的通用非法参数异常。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(
            IllegalArgumentException e) {
        return failure(AppErrorCode.COMMON_INVALID_ARGUMENT, e.getMessage());
    }

    /** 处理未分类异常，不向前端泄漏底层异常细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        log.error("未处理的系统异常", e);
        return failure(AppErrorCode.SYSTEM_INTERNAL_ERROR);
    }

    // ==================== 统一响应构造 ====================

    /** 使用结构化错误码创建失败响应。 */
    private ResponseEntity<Result<Void>> failure(AppErrorCode errorCode) {
        return failure(errorCode, errorCode.getDefaultMessage());
    }

    /** 使用结构化错误码和对外消息创建失败响应。 */
    private ResponseEntity<Result<Void>> failure(
            AppErrorCode errorCode,
            String message) {
        Result<Void> result = Result.failure(errorCode, message);
        enrich(result, errorCode);
        return response(errorCode, result);
    }

    /** 补充所有异常响应共有的服务信息。 */
    private void enrich(Result<Void> result, AppErrorCode errorCode) {
        result.setService(serviceName);
        result.setErrorId(UUID.randomUUID().toString());
        if (result.getModule() == null) {
            result.setModule(moduleOf(errorCode.getCode()));
        }
    }

    /** 根据错误码前缀提取模块名称。 */
    private String moduleOf(String code) {
        int separator = code.indexOf('.');
        return separator > 0 ? code.substring(0, separator) : null;
    }

    /** 将错误码映射为真实 HTTP 状态码。 */
    private ResponseEntity<Result<Void>> response(
            AppErrorCode errorCode,
            Result<Void> result) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(result);
    }
}
