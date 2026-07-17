package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void shouldAllowNormalMultipartFileToEnterUploadController() {
        CredentialFileController controller = new CredentialFileController();
        MockMultipartFile file = new MockMultipartFile(
                "files", "credential.pdf", "application/pdf", "test-content".getBytes());

        Result<Map<String, Object>> result = controller.upload(List.of(file), null);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertNotNull(result.getData().get("uploadBatchId"));
        assertEquals(1, ((List<?>) result.getData().get("files")).size());
    }
}
