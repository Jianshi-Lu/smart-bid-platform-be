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
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {

    private static final Long BIDDER_ACCOUNT_ID = 55L;
    private static final String USERNAME = "bidder01";

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadFilesIndependentlyAndReturnVerifiedFiles() {
        FileUploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse response = service.upload(
                BIDDER_ACCOUNT_ID.intValue(),
                BIDDER_ACCOUNT_ID,
                USERNAME,
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
                response.getFiles().get(0).getOriginalSha256());
        assertNotEquals(
                response.getFiles().get(0).getOriginalFilePath(),
                response.getFiles().get(1).getOriginalFilePath());
        assertTrue(response.getFiles().get(0).getOriginalFilePath()
                .startsWith(USERNAME + "/license.pdf/original/"));
        assertTrue(Files.exists(
                tempDir.resolve(response.getFiles().get(0).getOriginalFilePath())));
    }

    @Test
    void shouldGenerateNewFileUuidForEveryUpload() {
        FileUploadService service = createService(new Sha256FileChecksumCalculator());

        UploadResponse first = service.upload(
                BIDDER_ACCOUNT_ID.intValue(),
                BIDDER_ACCOUNT_ID,
                USERNAME,
                List.of(item("license.pdf", "content")));
        UploadResponse second = service.upload(
                BIDDER_ACCOUNT_ID.intValue(),
                BIDDER_ACCOUNT_ID,
                USERNAME,
                List.of(item("license.pdf", "content")));

        assertTrue(first.getFiles().getFirst().getOriginalFilePath()
                .matches(USERNAME + "/license.pdf/original/[0-9a-f-]{36}_.*"));
        assertTrue(second.getFiles().getFirst().getOriginalFilePath()
                .matches(USERNAME + "/license.pdf/original/[0-9a-f-]{36}_.*"));
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
                        USERNAME,
                        List.of(item("license.pdf", "content"))));

        assertEquals(
                AppErrorCode.CREDENTIAL_FILE_INVALID_UPLOAD_REQUEST,
                exception.getErrorCode());
    }

    @Test
    void shouldKeepPreviousFilesAndMarkFailedFileWhenNextChecksumFails()
            throws Exception {
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

        UploadResponse response = service.upload(
                BIDDER_ACCOUNT_ID.intValue(),
                BIDDER_ACCOUNT_ID,
                USERNAME,
                List.of(
                        item("first.pdf", "first-content"),
                        item("second.pdf", "second-content")));

        // 第一个文件保持成功（对象保留），第二个文件失败留痕，不再整体回滚。
        assertEquals(2, response.getFiles().size());
        assertEquals(ConvertStatus.UPLOADED,
                response.getFiles().get(0).getConvertStatus());
        assertTrue(Files.exists(
                tempDir.resolve(response.getFiles().get(0).getOriginalFilePath())));
        assertEquals(ConvertStatus.FAILED,
                response.getFiles().get(1).getConvertStatus());
        assertEquals("UPLOAD", response.getFiles().get(1).getErrorStage());
        assertTrue(response.getFiles().get(1).getErrorMessage()
                .contains("checksum failed"));
    }

    @Test
    void shouldSoftDeleteCredentialFileWithDeletionStateMachine() throws Exception {
        FileUploadService service = new FileUploadService();
        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        FileStorage storage = mock(FileStorage.class);
        FileConvertService convertService = mock(FileConvertService.class);
        OperationHistoryService history = mock(OperationHistoryService.class);
        ReflectionTestUtils.setField(service, "credentialFileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        ReflectionTestUtils.setField(service, "fileConvertService", convertService);
        ReflectionTestUtils.setField(service, "operationHistoryService", history);

        CredentialFile file = new CredentialFile();
        file.setFileId(7L);
        file.setBidRecordId(3);
        file.setOriginalFilePath("bidder01/license.pdf/original/uuid_license.pdf");
        file.setMarkdownFilePath("bidder01/license.pdf/converted/uuid_license.md");
        file.setConvertStatus(ConvertStatus.CONVERTED);
        when(fileMapper.selectByIdIncludingDeleted(7L)).thenReturn(file);
        when(fileMapper.beginDeletion(eq(7L), any(), any())).thenReturn(1);
        when(fileMapper.markDeleted(eq(7L), any())).thenReturn(1);
        when(fileMapper.markDeletionSucceeded(7L)).thenReturn(1);

        service.delete(3, 7L);

        verify(storage).delete(file.getOriginalFilePath());
        verify(storage).delete(file.getMarkdownFilePath());
        verify(fileMapper).markDeleted(eq(7L), any());
        verify(fileMapper).markDeletionSucceeded(7L);
        verify(history).recordCredentialFile(
                eq(7L), eq(OperationType.DELETE), any(), isNull(), isNull());
        verify(convertService).syncBidRecordCredentials(3);
    }

    @Test
    void shouldMarkDeletionFailedWhenObjectDeleteFails() throws Exception {
        FileUploadService service = new FileUploadService();
        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        FileStorage storage = mock(FileStorage.class);
        FileConvertService convertService = mock(FileConvertService.class);
        OperationHistoryService history = mock(OperationHistoryService.class);
        ReflectionTestUtils.setField(service, "credentialFileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        ReflectionTestUtils.setField(service, "fileConvertService", convertService);
        ReflectionTestUtils.setField(service, "operationHistoryService", history);

        CredentialFile file = new CredentialFile();
        file.setFileId(7L);
        file.setBidRecordId(3);
        file.setOriginalFilePath("bidder01/license.pdf/original/uuid_license.pdf");
        file.setConvertStatus(ConvertStatus.UPLOADED);
        when(fileMapper.selectByIdIncludingDeleted(7L)).thenReturn(file);
        when(fileMapper.beginDeletion(eq(7L), any(), any())).thenReturn(1);
        doThrow(new IOException("minio down"))
                .when(storage).delete(file.getOriginalFilePath());

        AppException failure = assertThrows(
                AppException.class, () -> service.delete(3, 7L));

        assertEquals(AppErrorCode.CREDENTIAL_FILE_DELETE_FAILED,
                failure.getErrorCode());
        verify(fileMapper).markDeletionFailed(
                eq(7L), eq("DELETE_OBJECT_ORIGINAL"), anyString());
        verify(fileMapper, never()).markDeleted(eq(7L), any());
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
        Map<Long, CredentialFile> storedFiles = new HashMap<>();
        doAnswer(invocation -> {
            CredentialFile file = invocation.getArgument(0);
            file.setFileId(fileIdSequence.incrementAndGet());
            storedFiles.put(file.getFileId(), file);
            return 1;
        }).when(fileMapper).insert(any(CredentialFile.class));
        doAnswer(invocation -> {
            CredentialFile file = storedFiles.get(invocation.getArgument(0));
            if (file != null) {
                file.setOriginalFilePath(invocation.getArgument(1));
                file.setOriginalSha256(invocation.getArgument(2));
                file.setStorageType(invocation.getArgument(3));
            }
            return 1;
        }).when(fileMapper).updateOriginalMetadata(
                anyLong(), anyString(), anyString(), anyString());
        doAnswer(invocation -> {
            CredentialFile file = storedFiles.get(invocation.getArgument(0));
            if (file != null) {
                file.setConvertStatus(ConvertStatus.FAILED);
                file.setErrorMessage(invocation.getArgument(1));
                file.setErrorStage(invocation.getArgument(2));
            }
            return 1;
        }).when(fileMapper).markFailed(anyLong(), anyString(), anyString());
        when(fileMapper.selectById(anyLong()))
                .thenAnswer(invocation -> storedFiles.get(
                        invocation.getArgument(0)));
        ReflectionTestUtils.setField(service, "credentialFileMapper", fileMapper);

        FileConvertService convertService = mock(FileConvertService.class);
        ReflectionTestUtils.setField(service, "fileConvertService", convertService);
        ReflectionTestUtils.setField(
                service, "operationHistoryService",
                mock(OperationHistoryService.class));
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
