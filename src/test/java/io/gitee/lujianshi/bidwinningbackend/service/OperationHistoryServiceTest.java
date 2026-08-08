package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBidOperationHistory;
import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.CredentialFileOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidOperationHistoryMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.KnowledgeFileOperationHistoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 操作历史服务：快照序列化、操作人与查询委托。 */
class OperationHistoryServiceTest {

    @Test
    void shouldRecordInvitationWithOperatorAndJsonSnapshots() {
        InvitationForBidOperationHistoryMapper invitationMapper =
                mock(InvitationForBidOperationHistoryMapper.class);
        when(invitationMapper.insert(any())).thenReturn(1);
        OperationHistoryService service = new OperationHistoryService(
                invitationMapper,
                mock(BidHandlerInfoOperationHistoryMapper.class),
                mock(BidRecordOperationHistoryMapper.class),
                mock(CredentialFileOperationHistoryMapper.class),
                mock(KnowledgeFileOperationHistoryMapper.class));

        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BID_INVITER, 7L, "admin"));
        try {
            InvitationForBid before = new InvitationForBid();
            before.setInvitationId(1);
            before.setProjectNo("P-001");
            before.setStatus(BidNoticeStatus.DRAFT);

            InvitationForBid after = new InvitationForBid();
            after.setInvitationId(1);
            after.setProjectNo("P-001");
            after.setStatus(BidNoticeStatus.PUBLISHED);

            service.recordInvitation(1, OperationType.UPDATE, before, after, "发布");
        } finally {
            CurrentAccountContext.clear();
        }

        ArgumentCaptor<InvitationForBidOperationHistory> captor =
                ArgumentCaptor.forClass(InvitationForBidOperationHistory.class);
        verify(invitationMapper).insert(captor.capture());

        InvitationForBidOperationHistory history = captor.getValue();
        assertEquals(1, history.getInvitationId());
        assertEquals("UPDATE", history.getOperation());
        assertEquals("BID_INVITER", history.getOperatorType());
        assertEquals(7L, history.getOperatorId());
        assertEquals("发布", history.getRemark());
        assertTrue(history.getBeforeSnapshot().contains("\"projectNo\":\"P-001\""));
        assertTrue(history.getAfterSnapshot().contains("\"PUBLISHED\""));
    }

    @Test
    void shouldKeepDeleteSnapshotAndNullAfterSnapshot() {
        BidRecordOperationHistoryMapper bidRecordMapper =
                mock(BidRecordOperationHistoryMapper.class);
        when(bidRecordMapper.insert(any())).thenReturn(1);
        OperationHistoryService service = new OperationHistoryService(
                mock(InvitationForBidOperationHistoryMapper.class),
                mock(BidHandlerInfoOperationHistoryMapper.class),
                bidRecordMapper,
                mock(CredentialFileOperationHistoryMapper.class),
                mock(KnowledgeFileOperationHistoryMapper.class));

        io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord deleted =
                new io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord();
        deleted.setBidRecordId(9);
        deleted.setBidderName("测试投标方");

        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BIDDER, 3L, "bidder01"));
        try {
            service.recordBidRecord(9, OperationType.DELETE, deleted, null, null);
        } finally {
            CurrentAccountContext.clear();
        }

        ArgumentCaptor<io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecordOperationHistory>
                captor = ArgumentCaptor.forClass(
                io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecordOperationHistory.class);
        verify(bidRecordMapper).insert(captor.capture());

        var history = captor.getValue();
        assertEquals("DELETE", history.getOperation());
        assertEquals("BIDDER", history.getOperatorType());
        assertEquals(3L, history.getOperatorId());
        assertTrue(history.getBeforeSnapshot().contains("\"bidderName\":\"测试投标方\""));
        assertNull(history.getAfterSnapshot());
    }
}
