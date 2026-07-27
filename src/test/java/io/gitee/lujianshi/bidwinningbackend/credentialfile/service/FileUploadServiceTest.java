package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.UploadItemProcessor;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class FileUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldProcessBatchInRequestOrderAndReturnVerifiedFiles() {
        FileUploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse response = service.upload(new UploadBatch(
                1001,
                List.of(
                        item("license.pdf", "first-content"),
                        item("license.pdf", "second-content"))));

        UUID.fromString(response.getUploadBatchId());
        assertEquals(2, response.getFiles().size());
        assertEquals(0, response.getFiles().get(0).getFileOrder());
        assertEquals(1, response.getFiles().get(1).getFileOrder());
        assertEquals(ConvertStatus.UPLOADED,
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
        FileUploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse first = service.upload(new UploadBatch(
                1001, List.of(item("license.pdf", "content"))));
        UploadResponse second = service.upload(new UploadBatch(
                1001, List.of(item("license.pdf", "content"))));

        assertNotEquals(first.getUploadBatchId(), second.getUploadBatchId());
        assertTrue(first.getFiles().getFirst().getOriginalFilePath()
                .startsWith("bid-records/1001/batches/"
                        + first.getUploadBatchId() + "/original/"));
        assertTrue(second.getFiles().getFirst().getOriginalFilePath()
                .startsWith("bid-records/1001/batches/"
                        + second.getUploadBatchId() + "/original/"));
    }

    @Test
    void shouldCreateNextBatchAndCloseItInUploadService() {
        FileUploadService service = createService(new Sha256FileChecksumCalculator());
        CredentialFileBatchMapper batchMapper =
                (CredentialFileBatchMapper) ReflectionTestUtils.getField(
                        service, "credentialFileBatchMapper");
        when(batchMapper.selectMaxBatchNo(1001)).thenReturn(2);

        UploadResponse response = service.upload(new UploadBatch(
                1001, List.of(item("license.pdf", "content"))));

        var batchCaptor = org.mockito.ArgumentCaptor.forClass(CredentialFileBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertEquals(3, batchCaptor.getValue().getBatchNo());
        assertEquals(response.getUploadBatchId(), batchCaptor.getValue().getUploadBatchId());
        verify(batchMapper).markUploaded(
                eq(response.getUploadBatchId()),
                eq(ConvertStatus.UPLOADED.name()),
                any());
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
        FileUploadService service = createService(failingCalculator);

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

    private FileUploadService createService(FileChecksumCalculator calculator) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        FileStorage storage = new LocalFileStorage(properties);

        UploadItemProcessor processor = new UploadItemProcessor();
        ReflectionTestUtils.setField(processor, "fileStorage", storage);
        ReflectionTestUtils.setField(processor, "fileChecksumCalculator", calculator);

        FileUploadService service = new FileUploadService();
        ReflectionTestUtils.setField(service, "uploadItemProcessor", processor);
        CredentialFileBatchMapper batchMapper = mock(CredentialFileBatchMapper.class);
        when(batchMapper.insert(any(CredentialFileBatch.class))).thenReturn(1);
        when(batchMapper.markUploaded(
                anyString(), anyString(), any())).thenReturn(1);
        ReflectionTestUtils.setField(service, "credentialFileBatchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        ReflectionTestUtils.setField(service, "storageProperties", properties);
        ReflectionTestUtils.setField(service, "credentialFileMapper", mock(CredentialFileMapper.class));
        return service;
    }

    private UploadItem item(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new UploadItem(
                filename,
                "application/pdf",
                bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }
}
