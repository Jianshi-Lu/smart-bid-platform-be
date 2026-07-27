package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturnClearMessageWhenUploadSizeExceeded() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(150L * 1024 * 1024));
        Result<Void> result = body(response);

        assertEquals(413, response.getStatusCode().value());
        assertFalse(result.isSuccess());
        assertEquals("COMMON.FILE_TOO_LARGE", result.getCode());
        assertEquals("文件大小超过限制", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void shouldReturnClearMessageWithoutLeakingIntegrityDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleAppException(
                new AppException(
                        AppErrorCode.CREDENTIAL_FILE_INTEGRITY_CHECK_FAILED,
                        "credential-file",
                        "UPLOAD",
                        "VERIFY_STORED_FILE",
                        "文件完整性校验失败"));
        Result<Void> result = body(response);

        assertEquals(500, response.getStatusCode().value());
        assertFalse(result.isSuccess());
        assertEquals("CREDENTIAL_FILE.INTEGRITY_CHECK_FAILED", result.getCode());
        assertEquals("文件完整性校验失败", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void shouldReturnConflictCodeWhenRiskAnalysisIsAlreadyRunning() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleAppException(
                new AppException(
                        AppErrorCode.RISK_ANALYSIS_IN_PROGRESS,
                        "risk-analysis",
                        "ANALYZE_RISK",
                        "CLAIM_ANALYSIS",
                        "该投标记录正在进行风险分析，请稍后再试: 8"));
        Result<Void> result = body(response);

        assertEquals(409, response.getStatusCode().value());
        assertFalse(result.isSuccess());
        assertEquals("RISK_ANALYSIS.IN_PROGRESS", result.getCode());
        assertEquals("该投标记录正在进行风险分析，请稍后再试: 8", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void shouldReturnConflictCodeWhenInvitationStatusHasChanged() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleAppException(
                new AppException(
                        AppErrorCode.INVITATION_STATUS_CONFLICT,
                        "invitation",
                        "STATUS_UPDATE",
                        "UPDATE_STATUS",
                        "招标公告状态已发生变化，请刷新后重试: invitationId=8, expectedStatus=DRAFT"));
        Result<Void> result = body(response);

        assertEquals(409, response.getStatusCode().value());
        assertFalse(result.isSuccess());
        assertEquals("INVITATION.STATUS_CONFLICT", result.getCode());
        assertEquals(
                "招标公告状态已发生变化，请刷新后重试: invitationId=8, expectedStatus=DRAFT",
                result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void shouldRouteDuplicateKeyToDatabaseDuplicateCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleDatabaseException(
                new DuplicateKeyException("duplicate"));
        Result<Void> result = body(response);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("DATABASE.DUPLICATE_KEY", result.getCode());
        assertEquals("database", result.getModule());
        assertEquals("DATABASE_ACCESS", result.getOperation());
    }

    @Test
    void shouldRouteConstraintViolationToDatabaseConstraintCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleDatabaseException(
                new DataIntegrityViolationException("constraint"));
        Result<Void> result = body(response);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("DATABASE.CONSTRAINT_VIOLATION", result.getCode());
    }

    @Test
    void shouldRouteDatabaseTimeoutToDatabaseTimeoutCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleDatabaseException(
                new QueryTimeoutException("timeout"));
        Result<Void> result = body(response);

        assertEquals(504, response.getStatusCode().value());
        assertEquals("DATABASE.QUERY_TIMEOUT", result.getCode());
    }

    @Test
    void shouldRouteDatabaseResourceFailureToConnectionCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Void>> response = handler.handleDatabaseException(
                new DataAccessResourceFailureException("connection"));
        Result<Void> result = body(response);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("DATABASE.CONNECTION_FAILED", result.getCode());
    }

    private Result<Void> body(ResponseEntity<Result<Void>> response) {
        assertNotNull(response.getBody());
        return response.getBody();
    }
}
