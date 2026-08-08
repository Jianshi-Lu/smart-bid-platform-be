package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.OperationHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileConvertServiceTest {

    private static final int BID_RECORD_ID = 1001;
    private static final String USERNAME = "bidder01";

    @TempDir
    Path tempDir;

    @Test
    void shouldReadOriginalCallPythonStoreAndVerifyMarkdown() throws Exception {
        String fileUuid = UUID.randomUUID().toString();
        byte[] original = "original-pdf-content"
                .getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# converted"
                .getBytes(StandardCharsets.UTF_8);
        FileChecksumCalculator checksumCalculator =
                new Sha256FileChecksumCalculator();
        FileStorage storage = createStorage();
        String originalPath = USERNAME + "/source.pdf/original/"
                + fileUuid + "_source.pdf";
        String originalSha256 = checksumCalculator.calculate(
                new ByteArrayInputStream(original));
        storage.store(new FileStorage.StoreRequest(
                originalPath,
                "application/pdf",
                original.length,
                Map.of(),
                new ByteArrayInputStream(original)));

        CredentialFile sourceFile = file(
                fileUuid,
                originalPath,
                original.length,
                originalSha256,
                ConvertStatus.UPLOADED);
        CredentialFile convertedFile = file(
                fileUuid,
                originalPath,
                original.length,
                originalSha256,
                ConvertStatus.CONVERTED);

        CredentialFileMapper fileMapper =
                mock(CredentialFileMapper.class);
        when(fileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(sourceFile), List.of(convertedFile));
        when(fileMapper.markConverting(1L)).thenReturn(1);
        when(fileMapper.markConverted(eq(1L), any(), any(), any()))
                .thenReturn(1);

        ToMarkdownClient toMarkdownClient =
                mock(ToMarkdownClient.class);
        when(toMarkdownClient.convert(
                any(InputStream.class),
                eq("source.pdf"),
                eq("application/pdf"),
                eq((long) original.length)))
                .thenAnswer(invocation -> {
                    InputStream requestStream = invocation.getArgument(0);
                    assertArrayEquals(original, requestStream.readAllBytes());
                    return new PythonMarkdownResponse(
                            markdown,
                            "source.md",
                            "text/markdown; charset=utf-8");
                });

        BidRecordService bidRecordService =
                mock(BidRecordService.class);
        when(bidRecordService.getById(BID_RECORD_ID))
                .thenReturn(new BidRecord());
        FileConvertService self = mock(FileConvertService.class);
        FileConvertService service = createService(
                fileMapper,
                toMarkdownClient,
                storage,
                checksumCalculator,
                bidRecordService,
                self);

        FileConvertResponse response =
                service.convertAll(BID_RECORD_ID);

        String markdownPath =
                USERNAME + "/source.pdf/converted/source.md";
        assertEquals(
                BidRecordCredentialConversionStatus.ALL_CONVERTED,
                response.status());
        assertEquals(1, response.totalFileCount());
        assertArrayEquals(
                markdown,
                storage.open(markdownPath).readAllBytes());
        verify(fileMapper).markConverted(
                eq(1L), eq(markdownPath), any(), any());
        verify(fileMapper, never()).markFailed(eq(1L), any(), any());
        verify(self).syncBidRecordCredentials(BID_RECORD_ID);
    }

    @Test
    void shouldClearCredentialProjectionWhenNoConvertedFileRemains() {
        CredentialFile failedFile = new CredentialFile();
        failedFile.setConvertStatus(ConvertStatus.FAILED);
        CredentialFileMapper fileMapper =
                mock(CredentialFileMapper.class);
        when(fileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(failedFile));
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        when(bidRecordMapper.selectByIdForUpdate(BID_RECORD_ID))
                .thenReturn(new BidRecord());
        when(bidRecordMapper.updateById(any(BidRecord.class)))
                .thenReturn(1);
        FileConvertService service = new FileConvertService();
        ReflectionTestUtils.setField(
                service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(
                service, "bidRecordMapper", bidRecordMapper);

        service.syncBidRecordCredentials(BID_RECORD_ID);

        ArgumentCaptor<BidRecord> patch =
                ArgumentCaptor.forClass(BidRecord.class);
        verify(bidRecordMapper).updateById(patch.capture());
        assertEquals(List.of(), patch.getValue().getCredentials());
    }

    private FileConvertService createService(
            CredentialFileMapper fileMapper,
            ToMarkdownClient toMarkdownClient,
            FileStorage storage,
            FileChecksumCalculator checksumCalculator,
            BidRecordService bidRecordService,
            FileConvertService self) {
        FileConvertService service = new FileConvertService();
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(
                service, "toMarkdownClient", toMarkdownClient);
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        ReflectionTestUtils.setField(
                service, "storageProperties", localStorageProperties());
        ReflectionTestUtils.setField(
                service, "fileChecksumCalculator", checksumCalculator);
        ReflectionTestUtils.setField(
                service, "bidRecordService", bidRecordService);
        ReflectionTestUtils.setField(
                service, "bidRecordMapper", mock(BidRecordMapper.class));
        ReflectionTestUtils.setField(service, "self", self);
        ReflectionTestUtils.setField(
                service, "operationHistoryService",
                mock(OperationHistoryService.class));
        return service;
    }

    private FileStorage createStorage() {
        FileStorageProperties properties = localStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalFileStorage(properties.getLocal().getRoot());
    }

    private FileStorageProperties localStorageProperties() {
        return new FileStorageProperties();
    }

    private CredentialFile file(
            String fileUuid,
            String originalPath,
            long fileSize,
            String sha256,
            ConvertStatus status) {
        CredentialFile file = new CredentialFile();
        file.setFileId(1L);
        file.setFileUuid(fileUuid);
        file.setBidRecordId(BID_RECORD_ID);
        file.setFileOrder(0);
        file.setOriginalFilename("source.pdf");
        file.setContentType("application/pdf");
        file.setFileSize(fileSize);
        file.setOriginalSha256(sha256);
        file.setStorageType("LOCAL");
        file.setOriginalFilePath(originalPath);
        file.setConvertStatus(status);
        if (status == ConvertStatus.CONVERTED) {
            file.setMarkdownFilePath(
                    StoragePath.createConvertedMarkdownPath(originalPath));
        }
        return file;
    }
}
