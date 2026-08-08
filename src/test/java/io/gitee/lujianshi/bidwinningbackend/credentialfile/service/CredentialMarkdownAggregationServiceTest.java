package io.gitee.lujianshi.bidwinningbackend.credentialfile.service;

import io.gitee.lujianshi.bidwinningbackend.config.RiskAnalysisProperties;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.entity.CredentialFile;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.enums.ConvertStatus;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.mapper.CredentialFileMapper;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.FileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.LocalFileStorage;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.storage.StoragePath;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidderAccountMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
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
    private static final Long BIDDER_ACCOUNT_ID = 55L;
    private static final String USERNAME = "bidder01";

    @TempDir
    Path tempDir;

    @Test
    void shouldMergeConvertedFilesInFileOrder() throws Exception {
        FileStorage storage = createStorage();

        String firstPath = storeMarkdown(storage, "first", "第一份材料");
        String secondPath = storeMarkdown(storage, "second", "第二份材料");
        String thirdPath = storeMarkdown(storage, "third", "第三份材料");

        CredentialFile first = file("first", 0, "first.pdf", firstPath);
        CredentialFile second = file("second", 1, "second.pdf", secondPath);
        CredentialFile third = file("third", 2, "third.pdf", thirdPath);

        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(first, second, third));
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        when(bidRecordMapper.selectById(BID_RECORD_ID))
                .thenReturn(accountBidRecord());

        CredentialMarkdownAggregationService service = createService(
                storage, fileMapper, bidRecordMapper, 131_072);

        String mergedPath = service.aggregate(BID_RECORD_ID);

        assertEquals(
                USERNAME + "/merged/" + BID_RECORD_ID
                        + "/credentials.md",
                mergedPath);
        String merged = new String(
                storage.open(mergedPath).readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(
                "# 投标记录资信材料汇总\n\n"
                        + "投标记录 ID：8\n\n"
                        + "### 文件：first.pdf\n\n---\n\n第一份材料\n\n---\n\n"
                        + "### 文件：second.pdf\n\n---\n\n第二份材料\n\n---\n\n"
                        + "### 文件：third.pdf\n\n---\n\n第三份材料\n\n---\n\n",
                merged);
    }

    @Test
    void shouldRejectAnyFileThatIsNotConverted() {
        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(failedFile()));
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        when(bidRecordMapper.selectById(BID_RECORD_ID))
                .thenReturn(accountBidRecord());

        CredentialMarkdownAggregationService service = createService(
                createStorage(), fileMapper, bidRecordMapper, 131_072);

        AppException exception = assertThrows(AppException.class,
                () -> service.aggregate(BID_RECORD_ID));

        assertEquals(AppErrorCode.RISK_ANALYSIS_INPUT_NOT_READY,
                exception.getErrorCode());
    }

    @Test
    void shouldRejectMergedMarkdownOverConfiguredLimit() throws Exception {
        FileStorage storage = createStorage();
        String markdownPath = storeMarkdown(storage, "large", "超过限制");
        CredentialFileMapper fileMapper = mock(CredentialFileMapper.class);
        when(fileMapper.selectByBidRecordId(BID_RECORD_ID))
                .thenReturn(List.of(file("large", 0, "large.pdf", markdownPath)));
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        when(bidRecordMapper.selectById(BID_RECORD_ID))
                .thenReturn(accountBidRecord());

        CredentialMarkdownAggregationService service = createService(
                storage, fileMapper, bidRecordMapper, 1);

        AppException exception = assertThrows(AppException.class,
                () -> service.aggregate(BID_RECORD_ID));

        assertEquals(AppErrorCode.RISK_ANALYSIS_INPUT_NOT_READY,
                exception.getErrorCode());
    }

    private CredentialMarkdownAggregationService createService(
            FileStorage storage,
            CredentialFileMapper fileMapper,
            BidRecordMapper bidRecordMapper,
            int maxCharacters) {
        RiskAnalysisProperties properties = new RiskAnalysisProperties();
        properties.setMaxMergedMarkdownChars(maxCharacters);
        CredentialMarkdownAggregationService service =
                new CredentialMarkdownAggregationService();
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "fileStorage", storage);
        ReflectionTestUtils.setField(service, "riskAnalysisProperties", properties);
        ReflectionTestUtils.setField(service, "bidRecordMapper", bidRecordMapper);
        BidderAccountMapper bidderAccountMapper = mock(BidderAccountMapper.class);
        BidderAccount bidderAccount = new BidderAccount();
        bidderAccount.setBidderAccountId(BIDDER_ACCOUNT_ID);
        bidderAccount.setUsername(USERNAME);
        when(bidderAccountMapper.selectById(BIDDER_ACCOUNT_ID))
                .thenReturn(bidderAccount);
        ReflectionTestUtils.setField(service, "bidderAccountMapper", bidderAccountMapper);
        return service;
    }

    private FileStorage createStorage() {
        var properties = new io.gitee.lujianshi.bidwinningbackend.credentialfile.config.FileStorageProperties();
        properties.getLocal().setRoot(tempDir.toString());
        return new LocalFileStorage(properties.getLocal().getRoot());
    }

    private String storeMarkdown(
            FileStorage storage,
            String fileUuid,
            String content) throws Exception {
        String path = StoragePath.createConvertedMarkdownPath(
                USERNAME + "/source.pdf/original/"
                        + fileUuid + "_source.pdf");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storage.store(new FileStorage.StoreRequest(
                path,
                "text/markdown",
                bytes.length,
                Map.of(),
                new ByteArrayInputStream(bytes)));
        return path;
    }

    private BidRecord accountBidRecord() {
        BidRecord record = new BidRecord();
        record.setBidRecordId(BID_RECORD_ID);
        record.setBidderAccountId(BIDDER_ACCOUNT_ID);
        return record;
    }

    private CredentialFile file(
            String fileUuid,
            int fileOrder,
            String filename,
            String markdownPath) {
        CredentialFile file = new CredentialFile();
        file.setFileId((long) (fileOrder + 1));
        file.setFileUuid(UUID.nameUUIDFromBytes(
                fileUuid.getBytes(StandardCharsets.UTF_8)).toString());
        file.setBidRecordId(BID_RECORD_ID);
        file.setFileOrder(fileOrder);
        file.setOriginalFilename(filename);
        file.setConvertStatus(ConvertStatus.CONVERTED);
        file.setMarkdownFilePath(markdownPath);
        return file;
    }

    private CredentialFile failedFile() {
        CredentialFile file = new CredentialFile();
        file.setFileId(99L);
        file.setBidRecordId(BID_RECORD_ID);
        file.setOriginalFilename("failed.pdf");
        file.setConvertStatus(ConvertStatus.FAILED);
        return file;
    }
}
