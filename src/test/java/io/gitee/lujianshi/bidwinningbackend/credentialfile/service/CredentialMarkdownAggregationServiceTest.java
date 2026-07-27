package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.config.RiskAnalysisProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFileBatch;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileBatchMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialMarkdownAggregationServiceTest {

    private static final int BID_RECORD_ID = 8;

    @TempDir
    Path tempDir;

    @Test
    void shouldMergeConvertedFilesInBatchAndFileOrder() throws Exception {
        String firstBatchId = UUID.randomUUID().toString();
        String secondBatchId = UUID.randomUUID().toString();
        FileStorage storage = createStorage();

        String firstPath = storeMarkdown(storage, firstBatchId, "first.md", "第一份材料");
        String secondPath = storeMarkdown(storage, firstBatchId, "second.md", "第二份材料");
        String thirdPath = storeMarkdown(storage, secondBatchId, "third.md", "第三份材料");

        CredentialFile first = file(firstBatchId, 0, "first.pdf", firstPath);
        CredentialFile second = file(firstBatchId, 1, "second.pdf", secondPath);
        CredentialFile third = file(secondBatchId, 0, "third.pdf", thirdPath);

        CredentialFileBatchMapper batchMapper = mock(CredentialFileBatchMapper.class);
        when(batchMapper.selectByBidRecordId(BID_RECORD_ID)).thenReturn(List.of(
                batch(firstBatchId, 1, 2),
                batch(secondBatchId, 2, 1)));
        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.selectByUploadBatchId(firstBatchId))
                .thenReturn(List.of(first, second));
        when(fileMapper.selectByUploadBatchId(secondBatchId))
                .thenReturn(List.of(third));

        CredentialMarkdownAggregationService service = createService(
                storage, batchMapper, fileMapper, 131_072);

        String mergedPath = service.aggregate(BID_RECORD_ID);

        String merged = new String(
                storage.open(mergedPath).readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(
                "# 投标记录资信材料汇总\n\n"
                        + "投标记录 ID：8\n\n"
                        + "## 批次 1\n\n"
                        + "### 文件：first.pdf\n\n---\n\n第一份材料\n\n---\n\n"
                        + "### 文件：second.pdf\n\n---\n\n第二份材料\n\n---\n\n"
                        + "## 批次 2\n\n"
                        + "### 文件：third.pdf\n\n---\n\n第三份材料\n\n---\n\n",
                merged);
    }

    @Test
    void shouldRejectAnyBatchThatIsNotConverted() {
        CredentialFileBatchMapper batchMapper = mock(CredentialFileBatchMapper.class);
        when(batchMapper.selectByBidRecordId(BID_RECORD_ID)).thenReturn(List.of(
                batch(UUID.randomUUID().toString(), 1, 1, ConvertStatus.FAILED)));

        CredentialMarkdownAggregationService service = createService(
                createStorage(), batchMapper, mock(CredentialFileMapper.class), 131_072);

        assertThrows(IllegalArgumentException.class,
                () -> service.aggregate(BID_RECORD_ID));
    }

    @Test
    void shouldRejectMergedMarkdownOverConfiguredLimit() throws Exception {
        String batchId = UUID.randomUUID().toString();
        FileStorage storage = createStorage();
        String markdownPath = storeMarkdown(storage, batchId, "large.md", "超过限制");
        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.selectByUploadBatchId(batchId)).thenReturn(List.of(
                file(batchId, 0, "large.pdf", markdownPath)));
        CredentialFileBatchMapper batchMapper = mock(CredentialFileBatchMapper.class);
        when(batchMapper.selectByBidRecordId(BID_RECORD_ID)).thenReturn(List.of(
                batch(batchId, 1, 1)));

        CredentialMarkdownAggregationService service = createService(
                storage, batchMapper, fileMapper, 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.aggregate(BID_RECORD_ID));
    }

    private CredentialMarkdownAggregationService createService(
            FileStorage storage,
            CredentialFileBatchMapper batchMapper,
            CredentialFileMapper fileMapper,
            int maxCharacters) {
        RiskAnalysisProperties properties = new RiskAnalysisProperties();
        properties.setMaxMergedMarkdownChars(maxCharacters);
        CredentialMarkdownAggregationService service =
                new CredentialMarkdownAggregationService();
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        ReflectionTestUtils.setField(service, "riskAnalysisProperties", properties);
        return service;
    }

    private FileStorage createStorage() {
        var properties = new io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalFileStorage(properties);
    }

    private String storeMarkdown(
            FileStorage storage,
            String batchId,
            String filename,
            String content) throws Exception {
        String path = "bid-records/" + BID_RECORD_ID + "/batches/"
                + batchId + "/converted/" + filename;
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.store(new FileStorage.StoreRequest(
                path,
                "text/markdown",
                bytes.length,
                Map.of(),
                new ByteArrayInputStream(bytes)));
        return path;
    }

    private CredentialFileBatch batch(
            String batchId,
            int batchNo,
            int fileCount) {
        return batch(batchId, batchNo, fileCount, ConvertStatus.CONVERTED);
    }

    private CredentialFileBatch batch(
            String batchId,
            int batchNo,
            int fileCount,
            ConvertStatus status) {
        CredentialFileBatch batch = new CredentialFileBatch();
        batch.setUploadBatchId(batchId);
        batch.setBidRecordId(BID_RECORD_ID);
        batch.setBatchNo(batchNo);
        batch.setStatus(status);
        batch.setFileCount(fileCount);
        batch.setConvertedFileCount(fileCount);
        return batch;
    }

    private CredentialFile file(
            String batchId,
            int fileOrder,
            String filename,
            String markdownPath) {
        CredentialFile file = new CredentialFile();
        file.setFileId((long) fileOrder + 1);
        file.setUploadBatchId(batchId);
        file.setBidRecordId(BID_RECORD_ID);
        file.setFileOrder(fileOrder);
        file.setOriginalFilename(filename);
        file.setStatus(ConvertStatus.CONVERTED);
        file.setMarkdownFilePath(markdownPath);
        return file;
    }
}
