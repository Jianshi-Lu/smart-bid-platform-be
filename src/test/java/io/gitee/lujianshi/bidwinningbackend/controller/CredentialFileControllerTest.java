package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.credentialfile.CredentialFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.CredentialFileUploadService;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage.LocalCredentialFileStorage;
import io.gitee.lujianshi.bidwinningbackend.service.filedigest.Sha256FileDigestCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CredentialFileControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDelegateMultipartUploadAndReturnTypedResponse() {
        CredentialFileUploadService uploadService = new CredentialFileUploadService();
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        ReflectionTestUtils.setField(
                uploadService,
                "credentialFileStorage",
                new LocalCredentialFileStorage(properties));
        ReflectionTestUtils.setField(
                uploadService,
                "fileDigestCalculator",
                new Sha256FileDigestCalculator());
        CredentialFileController controller = new CredentialFileController();
        ReflectionTestUtils.setField(controller, "credentialFileUploadService", uploadService);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "credential.pdf",
                "application/pdf",
                "test-content".getBytes(StandardCharsets.UTF_8));

        Result<CredentialFileUploadResponse> result = controller.upload(
                List.of(file), 1001, null);

        assertEquals(200, result.getCode());
        assertEquals("上传成功", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getFiles().size());
    }
}
