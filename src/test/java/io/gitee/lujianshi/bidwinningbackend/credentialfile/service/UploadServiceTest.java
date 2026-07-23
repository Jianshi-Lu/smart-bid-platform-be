package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.CredentialFileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.CredentialFileConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.CredentialFileBatchStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.PendingUploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.integrity.StoredFileIntegrityVerifier;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.PendingUploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
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

class UploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldProcessBatchInRequestOrderAndReturnVerifiedFiles() {
        UploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse response = service.upload(new UploadBatch(
                1001,
                List.of(
                        item("license.pdf", "first-content"),
                        item("license.pdf", "second-content"))));

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
        assertTrue(Files.exists(
                tempDir.resolve(response.getFiles().get(0).getOriginalFilePath())));
    }

    @Test
    void shouldGenerateNewBatchIdForEveryUploadRequest() {
        UploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse first = service.upload(new UploadBatch(
                1001, List.of(item("license.pdf", "content"))));
        UploadResponse second = service.upload(new UploadBatch(
                1001, List.of(item("license.pdf", "content"))));

        assertNotEquals(first.getUploadBatchId(), second.getUploadBatchId());
        assertTrue(first.getFiles().getFirst().getOriginalFilePath()
                .startsWith(first.getUploadBatchId() + "/original/"));
        assertTrue(second.getFiles().getFirst().getOriginalFilePath()
                .startsWith(second.getUploadBatchId() + "/original/"));
    }

    @Test
    void shouldDeletePreviouslyVerifiedFilesWhenNextChecksumFails() throws Exception {
        Sha256FileChecksumCalculator delegate = new Sha256FileChecksumCalculator();
        FileChecksumCalculator failingCalculator = new FileChecksumCalculator() {

            private int invocationCount;

            @Override
            public String algorithm() {
                return delegate.algorithm();
            }

            @Override
            public String calculate(InputStream input) throws IOException {
                invocationCount++;
                if (invocationCount == 3) {
                    throw new IOException("checksum failed");
                }
                return delegate.calculate(input);
            }
        };
        UploadService service = createService(failingCalculator);

        assertThrows(
                IllegalStateException.class,
                () -> service.upload(new UploadBatch(
                        1001,
                        List.of(
                                item("first.pdf", "first-content"),
                                item("second.pdf", "second-content")))));

        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    private UploadService createService(FileChecksumCalculator calculator) {
        CredentialFileStorageProperties properties = new CredentialFileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        FileStorage storage = new LocalFileStorage(properties);

        StoredFileIntegrityVerifier verifier = new StoredFileIntegrityVerifier();
        ReflectionTestUtils.setField(verifier, "fileStorage", storage);
        ReflectionTestUtils.setField(verifier, "fileChecksumCalculator", calculator);

        PendingUploadItemProcessor processor = new PendingUploadItemProcessor();
        ReflectionTestUtils.setField(processor, "fileStorage", storage);
        ReflectionTestUtils.setField(processor, "fileChecksumCalculator", calculator);
        ReflectionTestUtils.setField(processor, "storedFileIntegrityVerifier", verifier);

        UploadService service = new UploadService();
        ReflectionTestUtils.setField(service, "pendingUploadItemProcessor", processor);
        ReflectionTestUtils.setField(
                service, "credentialFileBatchService",
                new TestCredentialFileBatchService());
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        return service;
    }

    private static final class TestCredentialFileBatchService
            extends CredentialFileBatchService {

        @Override
        public CredentialFileBatch createUploadingBatch(
                Integer bidRecordId,
                int fileCount) {
            CredentialFileBatch batch = new CredentialFileBatch();
            batch.setUploadBatchId(UUID.randomUUID().toString());
            batch.setBidRecordId(bidRecordId);
            batch.setBatchNo(1);
            batch.setBatchStatus(CredentialFileBatchStatus.UPLOADING);
            batch.setFileCount(fileCount);
            batch.setConvertedFileCount(0);
            return batch;
        }

        @Override
        public void markUploaded(String uploadBatchId) {
            // 测试上传编排，不连接数据库。
        }
    }

    private PendingUploadItem item(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new PendingUploadItem(
                filename,
                "application/pdf",
                bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }
}
