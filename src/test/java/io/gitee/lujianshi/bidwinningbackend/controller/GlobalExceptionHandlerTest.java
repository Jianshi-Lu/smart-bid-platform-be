package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
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
}
