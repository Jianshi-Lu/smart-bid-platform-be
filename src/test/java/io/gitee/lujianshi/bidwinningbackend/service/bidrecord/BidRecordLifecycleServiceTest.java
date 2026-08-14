package io.gitee.lujianshi.bidwinningbackend.service.bidrecord;

import io.gitee.lujianshi.bidwinningbackend.credentialfile.service.FileUploadService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidderAccountMapper;
import io.gitee.lujianshi.bidwinningbackend.service.InvitationForBidService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BidRecordLifecycleServiceTest {

    @Test
    void shouldIndexBeforeReportingCreateSuccess() {
        Fixture fixture = fixture();
        BidRecord input = new BidRecord();
        input.setInvitationId(6);
        BidRecord indexed = record(13, 7L);
        when(fixture.bidRecordService().add(input)).thenAnswer(invocation -> {
            input.setBidRecordId(13);
            return 1;
        });
        when(fixture.vectorIndexService().reindex(13)).thenReturn(indexed);

        BidRecord result = fixture.lifecycle().create(input);

        assertEquals(indexed, result);
        InOrder order = inOrder(
                fixture.bidRecordService(), fixture.vectorIndexService());
        order.verify(fixture.bidRecordService()).add(input);
        order.verify(fixture.vectorIndexService()).reindex(13);
    }

    @Test
    void shouldRejectBidWhenInvitationIsNotPublished() {
        Fixture fixture = fixture();
        BidRecord input = new BidRecord();
        input.setInvitationId(8);
        when(fixture.invitationForBidService().getById(8))
                .thenReturn(invitation(BidNoticeStatus.DRAFT, null));

        AppException failure = assertThrows(
                AppException.class,
                () -> fixture.lifecycle().create(input));

        assertEquals(AppErrorCode.BID_RECORD_BIDDING_NOT_OPEN,
                failure.getErrorCode());
        assertEquals("CHECK_INVITATION_STATUS", failure.getStage());
        verifyNoInteractions(fixture.bidRecordService(), fixture.vectorIndexService());
    }

    @Test
    void shouldCreateBidWhenPublishedEvenIfBusinessDeadlineHasPassed() {
        Fixture fixture = fixture();
        BidRecord input = new BidRecord();
        input.setInvitationId(3);
        when(fixture.invitationForBidService().getById(3)).thenReturn(invitation(
                BidNoticeStatus.PUBLISHED,
                LocalDateTime.now().minusMinutes(1)));
        BidRecord indexed = record(14, 7L);
        when(fixture.bidRecordService().add(input)).thenAnswer(invocation -> {
            input.setBidRecordId(14);
            return 1;
        });
        when(fixture.vectorIndexService().reindex(14)).thenReturn(indexed);

        BidRecord result = fixture.lifecycle().create(input);

        assertEquals(indexed, result);
        verify(fixture.bidRecordService()).add(input);
        verify(fixture.vectorIndexService()).reindex(14);
    }

    @Test
    void shouldResolveBidderUsernameAndDeleteEveryLayerBeforeParent() {
        Fixture fixture = fixture();
        BidRecord record = record(13, 7L);
        BidderAccount account = new BidderAccount();
        account.setBidderAccountId(7L);
        account.setUsername("bidder01");
        when(fixture.bidRecordService().getById(13)).thenReturn(record);
        when(fixture.bidderAccountMapper().selectById(7L)).thenReturn(account);
        when(fixture.bidRecordMapper().beginDeletion(any(), any(), any()))
                .thenReturn(1);
        when(fixture.bidRecordService().deleteWithRiskAnalyses(13)).thenReturn(1);

        BidRecord deleted = fixture.lifecycle().delete(13, null);

        assertEquals(record, deleted);
        InOrder order = inOrder(
                fixture.bidRecordMapper(),
                fixture.fileUploadService(),
                fixture.vectorIndexService(),
                fixture.bidRecordService());
        order.verify(fixture.bidRecordMapper())
                .beginDeletion(any(), any(), any());
        order.verify(fixture.fileUploadService())
                .deleteAllForBidRecord(13, "bidder01");
        order.verify(fixture.vectorIndexService()).deleteAllMaterials(13);
        order.verify(fixture.bidRecordService()).deleteWithRiskAnalyses(13);
    }

    @Test
    void shouldKeepCreatedFactWhenDerivedIndexFails() {
        Fixture fixture = fixture();
        BidRecord input = new BidRecord();
        input.setInvitationId(6);
        BidRecord failedIndexRecord = record(13, 7L);
        failedIndexRecord.setIndexStatus("FAILED");
        when(fixture.bidRecordService().add(input)).thenAnswer(invocation -> {
            input.setBidRecordId(13);
            return 1;
        });
        when(fixture.vectorIndexService().reindex(13))
                .thenThrow(new IllegalStateException("chat-agent unavailable"));
        when(fixture.bidRecordService().getById(13)).thenReturn(failedIndexRecord);

        BidRecord result = fixture.lifecycle().create(input);

        assertEquals("FAILED", result.getIndexStatus());
        verify(fixture.bidRecordService()).getById(13);
    }

    private Fixture fixture() {
        BidRecordService bidRecordService = mock(BidRecordService.class);
        BidRecordVectorIndexService vectorIndexService =
                mock(BidRecordVectorIndexService.class);
        FileUploadService fileUploadService = mock(FileUploadService.class);
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        BidderAccountMapper bidderAccountMapper = mock(BidderAccountMapper.class);
        InvitationForBidService invitationForBidService =
                mock(InvitationForBidService.class);
        when(invitationForBidService.getById(any()))
                .thenReturn(invitation(
                        BidNoticeStatus.PUBLISHED,
                        LocalDateTime.now().plusDays(1)));
        BidRecordLifecycleService lifecycle = new BidRecordLifecycleService();
        ReflectionTestUtils.setField(
                lifecycle, "bidRecordService", bidRecordService);
        ReflectionTestUtils.setField(
                lifecycle, "vectorIndexService", vectorIndexService);
        ReflectionTestUtils.setField(
                lifecycle, "fileUploadService", fileUploadService);
        ReflectionTestUtils.setField(
                lifecycle, "bidRecordMapper", bidRecordMapper);
        ReflectionTestUtils.setField(
                lifecycle, "bidderAccountMapper", bidderAccountMapper);
        ReflectionTestUtils.setField(
                lifecycle, "invitationForBidService", invitationForBidService);
        return new Fixture(
                lifecycle,
                bidRecordService,
                vectorIndexService,
                fileUploadService,
                bidRecordMapper,
                bidderAccountMapper,
                invitationForBidService);
    }

    private BidRecord record(Integer id, Long accountId) {
        BidRecord record = new BidRecord();
        record.setBidRecordId(id);
        record.setBidderAccountId(accountId);
        return record;
    }

    private InvitationForBid invitation(
            BidNoticeStatus status,
            LocalDateTime businessDeadline) {
        InvitationForBid invitation = new InvitationForBid();
        invitation.setStatus(status);
        invitation.setBusinessTenderDeadline(businessDeadline);
        return invitation;
    }

    private record Fixture(
            BidRecordLifecycleService lifecycle,
            BidRecordService bidRecordService,
            BidRecordVectorIndexService vectorIndexService,
            FileUploadService fileUploadService,
            BidRecordMapper bidRecordMapper,
            BidderAccountMapper bidderAccountMapper,
            InvitationForBidService invitationForBidService) {
    }
}
