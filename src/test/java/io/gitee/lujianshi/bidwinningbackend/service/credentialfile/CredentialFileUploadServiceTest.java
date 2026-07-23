package io.gitee.lujianshi.bidwinningbackend.service.credentialfile;

import io.gitee.lujianshi.bidwinningbackend.config.storage.CredentialFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.dto.credentialfile.CredentialFileUploadResponse;
import io.gitee.lujianshi.bidwinningbackend.enums.CredentialFileConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage.CredentialFileStorage;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.storage.LocalCredentialFileStorage;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.digest.FileDigestCalculator;
import io.gitee.lujianshi.bidwinningbackend.service.credentialfile.digest.Sha256FileDigestCalculator;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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

        CredentialFileUploadResponse response = service.upload(
                List.of(first, second), 1001, null);

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
                List.of(file("license.pdf", "content")), 1001, batchId);

        assertEquals(batchId, response.getUploadBatchId());
        assertTrue(response.getFiles().get(0).getOriginalFilePath().startsWith(batchId + "/original/"));
    }

    @Test
    void shouldCalculateDigestBeforeStorageWithoutReopeningStoredFile() {
        RecordingLocalCredentialFileStorage storage = createRecordingStorage();
        Sha256FileDigestCalculator delegate = new Sha256FileDigestCalculator();
        FileDigestCalculator calculator = new FileDigestCalculator() {
            @Override
            public String algorithm() {
                return delegate.algorithm();
            }

            @Override
            public String calculate(InputStream input) throws IOException {
                assertNull(storage.storeRequest);
                return delegate.calculate(input);
            }
        };
        CredentialFileUploadService service = createService(calculator, storage);

        CredentialFileUploadResponse response = service.upload(
                List.of(file("license.pdf", "first-content")), 1001, null);

        String expectedSha256 =
                "9cd4aefcb376e737ae07795b61419408249765494085c144e2d83561de56d145";
        assertEquals(expectedSha256, storage.storeRequest.sha256());
        assertEquals(expectedSha256, response.getFiles().getFirst().getSha256());
        assertEquals(0, storage.openCalls);
    }

    @Test
    void shouldRejectInvalidBatchIdAndEmptyFile() {
        CredentialFileUploadService service = createService(new Sha256FileDigestCalculator());

        IllegalArgumentException invalidBidRecordId = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(List.of(file("license.pdf", "content")), 0, null));
        IllegalArgumentException invalidBatch = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(List.of(file("license.pdf", "content")), 1001, "../invalid"));
        IllegalArgumentException emptyFile = assertThrows(
                IllegalArgumentException.class,
                () -> service.upload(List.of(file("empty.pdf", "")), 1001, null));

        assertEquals("投标记录 ID 不正确", invalidBidRecordId.getMessage());
        assertEquals("上传批次号格式不正确", invalidBatch.getMessage());
        assertEquals("上传文件不能为空", emptyFile.getMessage());
    }

    @Test
    void shouldDeletePreviouslyStoredFilesWhenNextDigestFails() throws Exception {
        Sha256FileDigestCalculator delegate = new Sha256FileDigestCalculator();
        FileDigestCalculator failingCalculator = new FileDigestCalculator() {

            private int invocationCount;

            @Override
            public String algorithm() {
                return delegate.algorithm();
            }

            @Override
            public String calculate(InputStream input) throws IOException {
                invocationCount++;
                if (invocationCount == 2) {
                    throw new IOException("digest failed");
                }
                return delegate.calculate(input);
            }
        };
        CredentialFileUploadService service = createService(failingCalculator);

        assertThrows(
                IllegalStateException.class,
                () -> service.upload(List.of(
                        file("first.pdf", "first-content"),
                        file("second.pdf", "second-content")), 1001, null));

        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    private CredentialFileUploadService createService(FileDigestCalculator calculator) {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return createService(calculator, new LocalCredentialFileStorage(properties));
    }

    private CredentialFileUploadService createService(
            FileDigestCalculator calculator,
            CredentialFileStorage storage) {
        CredentialFileUploadService service = new CredentialFileUploadService();
        ReflectionTestUtils.setField(
                service,
                "credentialFileStorage",
                storage);
        ReflectionTestUtils.setField(service, "fileDigestCalculator", calculator);
        return service;
    }

    private RecordingLocalCredentialFileStorage createRecordingStorage() {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new RecordingLocalCredentialFileStorage(properties);
    }

    private MockMultipartFile file(String filename, String content) {
        return new MockMultipartFile(
                "files",
                filename,
                "application/pdf",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingLocalCredentialFileStorage
            extends LocalCredentialFileStorage {

        private CredentialFileStorage.StoreOriginalRequest storeRequest;
        private int openCalls;

        private RecordingLocalCredentialFileStorage(
                CredentialFileStorageProperties properties) {
            super(properties);
        }

        @Override
        public StoredFile storeOriginal(StoreOriginalRequest request) throws IOException {
            this.storeRequest = request;
            return super.storeOriginal(request);
        }

        @Override
        public InputStream open(String relativePath) {
            openCalls++;
            throw new AssertionError("上传流程不应重新读取已持久化文件");
        }
    }
}
