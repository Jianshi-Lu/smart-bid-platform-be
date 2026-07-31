package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum.Sha256FileChecksumCalculator;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.ToMarkdownClient;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.client.dto.PythonMarkdownResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.FileConvertResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.BidRecordCredentialConversionStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.entity.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.service.BidRecordService;
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

    @TempDir
    Path tempDir;

    @Test
    void shouldReadOriginalCallPythonStoreAndVerifyMarkdown() throws Exception {
        String batchId = UUID.randomUUID().toString();
        byte[] original = "original-pdf-content"
                .getBytes(StandardCharsets.UTF_8);
        byte[] markdown = "# converted"
                .getBytes(StandardCharsets.UTF_8);
        FileChecksumCalculator checksumCalculator =
                new Sha256FileChecksumCalculator();
        FileStorage storage = createStorage();
        String originalPath = "bid-records/" + BID_RECORD_ID
                + "/batches/" + batchId + "/original/source.pdf";
        String originalSha256 = checksumCalculator.calculate(
                new ByteArrayInputStream(original));
        storage.store(new FileStorage.StoreRequest(
                originalPath,
                "application/pdf",
                original.length,
                Map.of(),
                new ByteArrayInputStream(original)));

        CredentialFile sourceFile = file(
                batchId,
                originalPath,
                original.length,
                originalSha256,
                ConvertStatus.UPLOADED);
        CredentialFile convertedFile = file(
                batchId,
                originalPath,
                original.length,
                originalSha256,
                ConvertStatus.CONVERTED);
        CredentialFileBatch uploadedBatch =
                batch(batchId, ConvertStatus.UPLOADED, 0);
        CredentialFileBatch convertedBatch =
                batch(batchId, ConvertStatus.CONVERTED, 1);

        CredentialFileBatchMapper batchMapper =
                mock(CredentialFileBatchMapper.class);
        when(batchMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(uploadedBatch), List.of(convertedBatch));
        when(batchMapper.claimConverting(eq(batchId), any()))
                .thenReturn(1);
        when(batchMapper.markConverted(eq(batchId), eq(1), any()))
                .thenReturn(1);

        CredentialFileMapper fileMapper =
                mock(CredentialFileMapper.class);
        when(fileMapper.selectByUploadBatchId(batchId))
                .thenReturn(List.of(sourceFile), List.of(convertedFile));
        when(fileMapper.markConverting(1L)).thenReturn(1);
        when(fileMapper.markConverted(eq(1L), any(), any()))
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
                batchMapper,
                fileMapper,
                toMarkdownClient,
                storage,
                checksumCalculator,
                bidRecordService,
                self);

        FileConvertResponse response =
                service.convertAllBatches(BID_RECORD_ID);

        String markdownPath =
                StoragePath.createConvertedMarkdownPath(originalPath);
        assertEquals(
                BidRecordCredentialConversionStatus.ALL_CONVERTED,
                response.status());
        assertArrayEquals(
                markdown,
                storage.open(markdownPath).readAllBytes());
        verify(fileMapper).markConverted(
                eq(1L), eq(markdownPath), any());
        verify(fileMapper, never()).markFailed(eq(1L), any());
        verify(self).syncBidRecordCredentials(BID_RECORD_ID);
    }

    @Test
    void shouldClearCredentialProjectionWhenNoConvertedFileRemains() {
        CredentialFile failedFile = new CredentialFile();
        failedFile.setStatus(ConvertStatus.FAILED);
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
            CredentialFileBatchMapper batchMapper,
            CredentialFileMapper fileMapper,
            ToMarkdownClient toMarkdownClient,
            FileStorage storage,
            FileChecksumCalculator checksumCalculator,
            BidRecordService bidRecordService,
            FileConvertService self) {
        FileConvertService service = new FileConvertService();
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
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
        return service;
    }

    private FileStorage createStorage() {
        FileStorageProperties properties = localStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalFileStorage(properties);
    }

    private FileStorageProperties localStorageProperties() {
        return new FileStorageProperties();
    }

    private CredentialFile file(
            String batchId,
            String originalPath,
            long fileSize,
            String sha256,
            ConvertStatus status) {
        CredentialFile file = new CredentialFile();
        file.setFileId(1L);
        file.setUploadBatchId(batchId);
        file.setBidRecordId(BID_RECORD_ID);
        file.setFileOrder(0);
        file.setOriginalFilename("source.pdf");
        file.setContentType("application/pdf");
        file.setFileSize(fileSize);
        file.setSha256(sha256);
        file.setStorageType("LOCAL");
        file.setOriginalFilePath(originalPath);
        file.setStatus(status);
        if (status == ConvertStatus.CONVERTED) {
            file.setMarkdownFilePath(
                    StoragePath.createConvertedMarkdownPath(originalPath));
        }
        return file;
    }

    private CredentialFileBatch batch(
            String batchId,
            ConvertStatus status,
            int convertedFileCount) {
        CredentialFileBatch batch = new CredentialFileBatch();
        batch.setUploadBatchId(batchId);
        batch.setBidRecordId(BID_RECORD_ID);
        batch.setBatchNo(1);
        batch.setStatus(status);
        batch.setFileCount(1);
        batch.setConvertedFileCount(convertedFileCount);
        return batch;
    }
}
