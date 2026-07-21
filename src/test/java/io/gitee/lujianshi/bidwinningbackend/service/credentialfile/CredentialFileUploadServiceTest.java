package io.gitee.lujianshi.bidwinningbackend.service.credentialfile;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.dto.credentialfile.CredentialFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.enums.CredentialFileConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage.LocalCredentialFileStorage;
import io.gitee.lujianshi.bidwinningbackend.service.filedigest.FileDigestCalculator;
import io.gitee.lujianshi.bidwinningbackend.service.filedigest.Sha256FileDigestCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialFileUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreMultipleFilesAndReturnDigestMetadata() throws Exception {
        CredentialFileUploadService service = createService(new Sha256FileDigestCalculator());
        MockMultipartFile first = file("license.pdf", "first-content");
        MockMultipartFile second = file("license.pdf", "second-content");

        CredentialFileUploadResponse response = service.upload(List.of(first, second), null);

        UUID.fromString(response.getUploadBatchId());
        assertEquals(2, response.getFiles().size());
        assertEquals(0, response.getFiles().get(0).getFileOrder());
        assertEquals(1, response.getFiles().get(1).getFileOrder());
        assertEquals(CredentialFileConvertStatus.UPLOADED,
                response.getFiles().get(0).getConvertStatus());
        assertEquals(
                "9cd4aefcb376e737ae07795b61419408249765494085c144e2d83561de56d145",
                response.getFiles().get(0).getSha256());
        assertNotEquals(
                response.getFiles().get(0).getOriginalFilePath(),
                response.getFiles().get(1).getOriginalFilePath());
        assertTrue(Files.exists(tempDir.resolve(response.getFiles().get(0).getOriginalFilePath())));
    }

    @Test
    void shouldReuseValidUploadBatchId() {
        CredentialFileUploadService service = createService(new Sha256FileDigestCalculator());
        String batchId = UUID.randomUUID().toString();

        CredentialFileUploadResponse response = service.upload(
                List.of(file("license.pdf", "content")), batchId);

        assertEquals(batchId, response.getUploadBatchId());
        assertTrue(response.getFiles().get(0).getOriginalFilePath().startsWith(batchId + "/original/"));
    }

    @Test
    void shouldRejectInvalidBatchIdAndEmptyFile() {
        CredentialFileUploadService service = createService(new Sha256FileDigestCalculator());

        IllegalArgumentException invalidBatch = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(List.of(file("license.pdf", "content")), "../invalid"));
        IllegalArgumentException emptyFile = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(List.of(file("empty.pdf", "")), null));

        assertEquals("上传批次号格式不正确", invalidBatch.getMessage());
        assertEquals("上传文件不能为空", emptyFile.getMessage());
    }

    @Test
    void shouldDeleteCurrentRequestFilesWhenDigestFails() throws Exception {
        FileDigestCalculator failingCalculator = new FileDigestCalculator() {
            @Override
            public String algorithm() {
                return "FAIL";
            }

            @Override
            public String calculate(InputStream input) throws IOException {
                throw new IOException("digest failed");
            }
        };
        CredentialFileUploadService service = createService(failingCalculator);

        assertThrows(
                IllegalStateException.class,
                () -> service.upload(List.of(file("license.pdf", "content")), null));

        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    private CredentialFileUploadService createService(FileDigestCalculator calculator) {
        CredentialFileUploadService service = new CredentialFileUploadService();
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        ReflectionTestUtils.setField(
                service,
                "credentialFileStorage",
                new LocalCredentialFileStorage(properties));
        ReflectionTestUtils.setField(service, "fileDigestCalculator", calculator);
        return service;
    }

    private MockMultipartFile file(String filename, String content) {
        return new MockMultipartFile(
                "files",
                filename,
                "application/pdf",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
