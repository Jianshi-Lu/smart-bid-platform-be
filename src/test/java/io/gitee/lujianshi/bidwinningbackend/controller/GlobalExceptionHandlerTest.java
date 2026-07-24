package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.FileChecksumMismatchException;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturnClearMessageWhenUploadSizeExceeded() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Result<Void> result = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(150L * 1024 * 1024));

        assertEquals(400, result.getCode());
        assertEquals("文件大小超过限制", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void shouldReturnClearMessageWithoutLeakingIntegrityDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Result<Void> result = handler.handleFileChecksumMismatch(
                new FileChecksumMismatchException(
                        "存储对象内容完整性校验失败: internal/object/key.pdf"));

        assertEquals(500, result.getCode());
        assertEquals("文件完整性校验失败", result.getMessage());
        assertNull(result.getData());
    }
}
