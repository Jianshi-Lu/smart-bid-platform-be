package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.processor.UploadItemProcessor;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class FileUploadServiceTest {

    private static final Long BIDDER_ACCOUNT_ID = 55L;

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadFilesIndependentlyAndReturnVerifiedFiles() {
        FileUploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse response = service.upload(
                BIDDER_ACCOUNT_ID.intValue(),
                BIDDER_ACCOUNT_ID,
                List.of(
                        item("license.pdf", "first-content"),
                        item("license.pdf", "second-content")));

        assertEquals(2, response.getFiles().size());
        assertEquals(0, response.getFiles().get(0).getFileOrder());
        assertEquals(1, response.getFiles().get(1).getFileOrder());
        assertNotNull(response.getFiles().get(0).getFileId());
        assertEquals(ConvertStatus.UPLOADED,
                response.getFiles().get(0).getConvertStatus());
        assertEquals(
                "9cd4aefcb376e737ae07795b61419408249765494085c144e2d83561de56d145",
                response.getFiles().get(0).getSha256());
        assertNotEquals(
                response.getFiles().get(0).getOriginalFilePath(),
                response.getFiles().get(1).getOriginalFilePath());
        assertTrue(response.getFiles().get(0).getOriginalFilePath()
                .startsWith(BIDDER_ACCOUNT_ID + "/files/"));
        assertTrue(Files.exists(
                tempDir.resolve(response.getFiles().get(0).getOriginalFilePath())));
    }

    @Test
    void shouldGenerateNewFileUuidForEveryUpload() {
        FileUploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse first = service.upload(
                BIDDER_ACCOUNT_ID.intValue(),
                BIDDER_ACCOUNT_ID,
                List.of(item("license.pdf", "content")));
        UploadResponse second = service.upload(
                BIDDER_ACCOUNT_ID.intValue(),
                BIDDER_ACCOUNT_ID,
                List.of(item("license.pdf", "content")));

        assertTrue(first.getFiles().getFirst().getOriginalFilePath()
                .matches(BIDDER_ACCOUNT_ID + "/files/[0-9a-f-]{36}/original/.*"));
        assertTrue(second.getFiles().getFirst().getOriginalFilePath()
                .matches(BIDDER_ACCOUNT_ID + "/files/[0-9a-f-]{36}/original/.*"));
        assertNotEquals(
                first.getFiles().getFirst().getOriginalFilePath(),
                second.getFiles().getFirst().getOriginalFilePath());
    }

    @Test
    void shouldRejectUploadWithoutBidderAccount() {
        FileUploadService service = createService(new Sha256FileChecksumCalculator());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.upload(
                        1001,
                        null,
                        List.of(item("license.pdf", "content"))));

        assertEquals(
                AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                exception.getErrorCode());
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

        AppException exception = assertThrows(
                AppException.class,
                () -> service.upload(
                        BIDDER_ACCOUNT_ID.intValue(),
                        BIDDER_ACCOUNT_ID,
                        List.of(
                                item("first.pdf", "first-content"),
                                item("second.pdf", "second-content"))));
        assertEquals(
                AppErrorCode.CREDENTIAL_FILE_STORAGE_FAILED,
                exception.getErrorCode());

        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(Files::isRegularFile));
        }
    }

    private FileUploadService createService(FileChecksumCalculator calculator) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        FileStorage storage = new LocalFileStorage(properties.getLocal().getRoot());

        UploadItemProcessor processor = new UploadItemProcessor();
        ReflectionTestUtils.setField(processor, "fileStorage", storage);
        ReflectionTestUtils.setField(processor, "fileChecksumCalculator", calculator);

        FileUploadService service = new FileUploadService();
        ReflectionTestUtils.setField(service, "uploadItemProcessor", processor);
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        ReflectionTestUtils.setField(service, "storageProperties", properties);

        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        AtomicLong fileIdSequence = new AtomicLong(100);
        doAnswer(invocation -> {
            CredentialFile file = invocation.getArgument(0);
            file.setFileId(fileIdSequence.incrementAndGet());
            return 1;
        }).when(fileMapper).insert(any(CredentialFile.class));
        ReflectionTestUtils.setField(service, "credentialFileMapper", fileMapper);

        FileConvertService convertService = mock(FileConvertService.class);
        ReflectionTestUtils.setField(service, "fileConvertService", convertService);
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
