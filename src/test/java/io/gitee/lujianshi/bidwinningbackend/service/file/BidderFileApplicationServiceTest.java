package io.gitee.lujianshi.bidwinningbackend.service.file;

import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.dto.UploadResponse;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.intake.UploadItem;
import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.realtime.FileStatusEventPublisher;
import io.gitee.lujianshi.bidwinningbackend.service.account.BidderAccountService;
import io.gitee.lujianshi.bidwinningbackend.service.file.FileDownloadService;
import io.gitee.lujianshi.bidwinningbackend.service.bidrecord.BidRecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidderFileApplicationServiceTest {

    @AfterEach
    void clearContext() {
        CurrentAccountContext.clear();
    }

    @Test
    void shouldAuthenticateOwnRecordAndKeepOperatorContextDuringUpload() {
        Fixture fixture = fixture();
        UploadResponse expected = new UploadResponse(List.of());
        List<UploadItem> files = List.of(new UploadItem(
                "license.pdf",
                "application/pdf",
                3,
                () -> new ByteArrayInputStream(new byte[] {1, 2, 3})));
        when(fixture.fileUploadService().upload(
                13, 7L, "bidder01", files)).thenAnswer(invocation -> {
                    CurrentAccountContext.CurrentAccount current =
                            CurrentAccountContext.get();
                    assertEquals(CurrentAccountContext.BIDDER,
                            current.operatorType());
                    assertEquals(7L, current.operatorId());
                    return expected;
                });

        UploadResponse actual = fixture.service().uploadCredentialFiles(
                13, "bidder01", "Basic token", files);

        assertSame(expected, actual);
        assertNull(CurrentAccountContext.get());
    }

    @Test
    void shouldDeleteOnlyAfterOwnershipCheckAndClearContext() {
        Fixture fixture = fixture();

        fixture.service().deleteCredentialFile(13, 71L, "Basic token");

        verify(fixture.bidRecordService()).getById(13);
        verify(fixture.fileUploadService()).delete(13, 71L);
        assertNull(CurrentAccountContext.get());
    }

    private Fixture fixture() {
        BidRecordService bidRecordService = mock(BidRecordService.class);
        FileUploadService fileUploadService = mock(FileUploadService.class);
        BidderAccountService accountService = mock(BidderAccountService.class);
        FileStatusEventPublisher eventPublisher =
                mock(FileStatusEventPublisher.class);
        FileDownloadService downloadService = mock(FileDownloadService.class);
        BidderAccount account = new BidderAccount();
        account.setBidderAccountId(7L);
        account.setUsername("bidder01");
        BidRecord record = new BidRecord();
        record.setBidRecordId(13);
        record.setBidderAccountId(7L);
        when(accountService.authenticateBasic("Basic token")).thenReturn(account);
        when(bidRecordService.getById(13)).thenReturn(record);
        return new Fixture(
                new BidderFileApplicationService(
                        bidRecordService,
                        fileUploadService,
                        accountService,
                        eventPublisher,
                        downloadService),
                bidRecordService,
                fileUploadService);
    }

    private record Fixture(
            BidderFileApplicationService service,
            BidRecordService bidRecordService,
            FileUploadService fileUploadService) {
    }
}
