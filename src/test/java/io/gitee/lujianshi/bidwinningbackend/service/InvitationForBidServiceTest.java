package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvitationForBidServiceTest {

    @Test
    void shouldRejectDeletingInvitationThatStillOwnsBidRecords() {
        InvitationForBidService service = new InvitationForBidService();
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        BidRecord record = new BidRecord();
        record.setBidRecordId(13);
        record.setInvitationId(7);
        when(bidRecordMapper.selectByInvitationId(7)).thenReturn(List.of(record));
        ReflectionTestUtils.setField(service, "bidRecordMapper", bidRecordMapper);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.delete(7));

        assertEquals(
                AppErrorCode.INVITATION_HAS_BID_RECORDS,
                exception.getErrorCode());
        assertEquals("CHECK_BID_RECORDS", exception.getStage());
    }

    @Test
    void shouldRejectWholeBatchWhenAnyInvitationOwnsBidRecords() {
        InvitationForBidService service = new InvitationForBidService();
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        when(bidRecordMapper.selectByInvitationId(7)).thenReturn(List.of());
        when(bidRecordMapper.selectByInvitationId(8))
                .thenReturn(List.of(new BidRecord()));
        ReflectionTestUtils.setField(service, "bidRecordMapper", bidRecordMapper);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.deleteByIds(List.of(7, 8)));

        assertEquals(
                AppErrorCode.INVITATION_HAS_BID_RECORDS,
                exception.getErrorCode());
    }

    @Test
    void shouldRecordDeleteHistoryAfterDeletingInvitationWithoutBidRecords() {
        InvitationForBidService service = new InvitationForBidService();
        BidRecordMapper bidRecordMapper = mock(BidRecordMapper.class);
        InvitationForBidMapper invitationMapper = mock(InvitationForBidMapper.class);
        OperationHistoryService operationHistoryService = mock(OperationHistoryService.class);
        InvitationForBid invitation = new InvitationForBid();
        invitation.setInvitationId(7);
        invitation.setProjectNo("DELETE-TEST");
        when(bidRecordMapper.selectByInvitationId(7)).thenReturn(List.of());
        when(invitationMapper.selectById(7)).thenReturn(invitation);
        when(invitationMapper.deleteById(7)).thenReturn(1);
        ReflectionTestUtils.setField(service, "bidRecordMapper", bidRecordMapper);
        ReflectionTestUtils.setField(service, "mapper", invitationMapper);
        ReflectionTestUtils.setField(service, "operationHistoryService", operationHistoryService);

        int deleted = service.delete(7);

        assertEquals(1, deleted);
        verify(operationHistoryService).recordInvitation(
                7,
                OperationType.DELETE,
                invitation,
                null,
                null);
    }
}
