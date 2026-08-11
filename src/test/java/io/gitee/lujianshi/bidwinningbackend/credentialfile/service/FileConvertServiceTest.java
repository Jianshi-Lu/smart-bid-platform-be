package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.CredentialFileVectorStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.model.PreparedCredentialDocument;
import io.gitee.lujianshi.bidwinningbackend.storage.object.ObjectStorage;
import io.gitee.lujianshi.bidwinningbackend.storage.object.LocalObjectStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordService;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordVectorIndexService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
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
        ObjectStorage storage = createStorage();
        String originalPath = USERNAME + "/source.pdf/original/"
                + fileUuid + "_source.pdf";
        String originalSha256 = checksumCalculator.calculate(
                new ByteArrayInputStream(original));
        storage.store(new ObjectStorage.StoreRequest(
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
        convertedFile.setConvertedSha256(checksumCalculator.calculate(
                new ByteArrayInputStream(markdown)));

        CredentialFileMapper fileMapper =
                mock(CredentialFileMapper.class);
        when(fileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(sourceFile), List.of(convertedFile));
        when(fileMapper.markConverting(1L)).thenReturn(1);
        when(fileMapper.reserveMarkdownPath(eq(1L), any())).thenReturn(1);
        when(fileMapper.markConverted(eq(1L), any(), any(), any()))
                .thenReturn(1);
        when(fileMapper.markVectorizing(1L)).thenReturn(1);
        when(fileMapper.markVectorized(eq(1L), eq(2), any()))
                .thenReturn(1);
        when(fileMapper.selectById(1L)).thenReturn(convertedFile);

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
        BidRecord bidRecord = new BidRecord();
        bidRecord.setBidderName("测试投标人");
        when(bidRecordService.getById(BID_RECORD_ID))
                .thenReturn(bidRecord);
        CredentialFileVectorStorage vectorClient =
                mock(CredentialFileVectorStorage.class);
        when(vectorClient.replaceDocument(any(PreparedCredentialDocument.class)))
                .thenReturn(2);
        FileConvertService self = mock(FileConvertService.class);
        FileConvertService service = createService(
                fileMapper,
                toMarkdownClient,
                storage,
                checksumCalculator,
                bidRecordService,
                self);
        ReflectionTestUtils.setField(
                service, "credentialVectorStorage", vectorClient);

        FileConvertResponse response =
                service.convertAll(BID_RECORD_ID);

        String markdownPath =
                USERNAME + "/source.pdf/converted/"
                        + fileUuid + "_source.md";
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
        verify(fileMapper).markVectorized(eq(1L), eq(2), any());
        ArgumentCaptor<PreparedCredentialDocument> vectorRequest =
                ArgumentCaptor.forClass(PreparedCredentialDocument.class);
        verify(vectorClient).replaceDocument(vectorRequest.capture());
        assertEquals(fileUuid, vectorRequest.getValue().fileUuid());
        assertEquals(BID_RECORD_ID, vectorRequest.getValue().bidRecordId());
        assertEquals("# converted", vectorRequest.getValue().markdownText());
        verify(self).syncBidRecordCredentials(BID_RECORD_ID);
    }

    @Test
    void shouldCleanVectorsAndMarkIndexFailedWhenCredentialVectorizationFails()
            throws Exception {
        byte[] markdown = "# persisted credential"
                .getBytes(StandardCharsets.UTF_8);
        FileChecksumCalculator checksumCalculator =
                new Sha256FileChecksumCalculator();
        ObjectStorage storage = createStorage();
        String fileUuid = UUID.randomUUID().toString();
        String originalPath = USERNAME + "/source.pdf/original/"
                + fileUuid + "_source.pdf";
        String markdownPath = StoragePath.createConvertedMarkdownPath(
                originalPath);
        storage.store(new ObjectStorage.StoreRequest(
                markdownPath,
                "text/markdown; charset=utf-8",
                markdown.length,
                Map.of(),
                new ByteArrayInputStream(markdown)));

        CredentialFile converted = file(
                fileUuid,
                originalPath,
                10L,
                "a".repeat(64),
                ConvertStatus.CONVERTED);
        converted.setMarkdownFilePath(markdownPath);
        converted.setConvertedSha256(checksumCalculator.calculate(
                new ByteArrayInputStream(markdown)));

        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.markVectorizing(1L)).thenReturn(1);
        when(fileMapper.selectById(1L)).thenReturn(converted);
        when(fileMapper.markVectorizeFailed(eq(1L), any())).thenReturn(1);
        BidRecord record = new BidRecord();
        record.setBidderName("测试投标人");
        BidRecordService bidRecordService = mock(BidRecordService.class);
        when(bidRecordService.getById(BID_RECORD_ID)).thenReturn(record);
        CredentialFileVectorStorage vectorClient =
                mock(CredentialFileVectorStorage.class);
        doThrow(new IllegalStateException("vector service unavailable"))
                .when(vectorClient)
                .replaceDocument(any(PreparedCredentialDocument.class));

        FileConvertService service = createService(
                fileMapper,
                mock(ToMarkdownClient.class),
                storage,
                checksumCalculator,
                bidRecordService,
                mock(FileConvertService.class));
        ReflectionTestUtils.setField(
                service, "credentialVectorStorage", vectorClient);

        ReflectionTestUtils.invokeMethod(
                service, "vectorizeConvertedFile", converted);

        verify(vectorClient, atLeastOnce()).deleteDocument(fileUuid);
        verify(fileMapper).markVectorizeFailed(
                eq(1L), eq("vector service unavailable"));
        verify(fileMapper, never()).markVectorized(eq(1L), anyInt(), any());
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

    @Test
    void shouldRebuildEmptyCredentialProjectionWhenNoActiveFileExists() {
        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of());
        BidRecordService bidRecordService = mock(BidRecordService.class);
        when(bidRecordService.getById(BID_RECORD_ID))
                .thenReturn(new BidRecord());
        FileConvertService self = mock(FileConvertService.class);
        FileConvertService service = createService(
                fileMapper,
                mock(ToMarkdownClient.class),
                mock(ObjectStorage.class),
                mock(FileChecksumCalculator.class),
                bidRecordService,
                self);

        FileConvertResponse response = service.convertAll(BID_RECORD_ID);

        assertEquals(
                BidRecordCredentialConversionStatus.ALL_CONVERTED,
                response.status());
        assertEquals(0, response.totalFileCount());
        verify(self).syncBidRecordCredentials(BID_RECORD_ID);
    }

    @Test
    void shouldKeepConvertedWhenCredentialProjectionSyncFails() throws Exception {
        String fileUuid = UUID.randomUUID().toString();
        byte[] original = "original-pdf-content"
                .getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# converted"
                .getBytes(StandardCharsets.UTF_8);
        FileChecksumCalculator checksumCalculator =
                new Sha256FileChecksumCalculator();
        ObjectStorage storage = createStorage();
        String originalPath = USERNAME + "/source.pdf/original/"
                + fileUuid + "_source.pdf";
        String originalSha256 = checksumCalculator.calculate(
                new ByteArrayInputStream(original));
        storage.store(new ObjectStorage.StoreRequest(
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
        sourceFile.setOriginalStorageStatus("STORED");
        CredentialFile convertingFile = file(
                fileUuid,
                originalPath,
                original.length,
                originalSha256,
                ConvertStatus.CONVERTING);
        convertingFile.setOriginalStorageStatus("STORED");
        CredentialFile convertedFile = file(
                fileUuid,
                originalPath,
                original.length,
                originalSha256,
                ConvertStatus.CONVERTED);
        convertedFile.setOriginalStorageStatus("STORED");

        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.markConverting(1L)).thenReturn(1);
        when(fileMapper.reserveMarkdownPath(eq(1L), any())).thenReturn(1);
        when(fileMapper.markConverted(eq(1L), any(), any(), any()))
                .thenReturn(1);
        when(fileMapper.selectById(1L)).thenReturn(
                convertingFile, convertedFile, convertedFile);

        ToMarkdownClient toMarkdownClient = mock(ToMarkdownClient.class);
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

        FileConvertService self = mock(FileConvertService.class);
        doThrow(new IllegalStateException("projection unavailable"))
                .when(self).syncBidRecordCredentials(BID_RECORD_ID);
        FileConvertService service = createService(
                fileMapper,
                toMarkdownClient,
                storage,
                checksumCalculator,
                mock(BidRecordService.class),
                self);
        FileStatusEventPublisher eventPublisher =
                mock(FileStatusEventPublisher.class);
        ReflectionTestUtils.setField(
                service, "fileStatusEventPublisher", eventPublisher);

        service.convertFile(sourceFile);

        verify(fileMapper, never()).markFailed(eq(1L), any(), any());
        verify(eventPublisher, atLeastOnce()).publish(
                eq(FileStatusEventPublisher.CREDENTIAL_TOPIC_PREFIX
                        + BID_RECORD_ID),
                eq("1"),
                eq("STORED"),
                eq("CONVERTED"),
                isNull());
    }

    private FileConvertService createService(
            CredentialFileMapper fileMapper,
            ToMarkdownClient toMarkdownClient,
            ObjectStorage storage,
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
        ReflectionTestUtils.setField(
                service, "credentialVectorStorage",
                mock(CredentialFileVectorStorage.class));
        ReflectionTestUtils.setField(
                service, "bidRecordVectorIndexService",
                mock(BidRecordVectorIndexService.class));
        return service;
    }

    private ObjectStorage createStorage() {
        FileStorageProperties properties = localStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalObjectStorage(properties.getLocal().getRoot());
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
