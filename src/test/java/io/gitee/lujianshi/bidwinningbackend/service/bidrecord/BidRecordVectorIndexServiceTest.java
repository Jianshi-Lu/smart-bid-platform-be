package io.gitee.lujianshi.bidwinningbackend.service.bidrecord;

import io.gitee.lujianshi.bidwinningbackend.storage.vector.BidRecordVectorStorage;
import io.gitee.lujianshi.bidwinningbackend.storage.vector.model.PreparedBidRecordDocument;
import io.gitee.lujianshi.bidwinningbackend.entity.bidrecord.BidRecord;
import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidRecordMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidRecordVectorIndexServiceTest {

    @Test
    void shouldIndexOneCompleteBidAndInvitationDocument() {
        BidRecordMapper recordMapper = mock(BidRecordMapper.class);
        InvitationForBidMapper invitationMapper = mock(InvitationForBidMapper.class);
        BidRecordVectorStorage client = mock(BidRecordVectorStorage.class);
        BidRecord record = record();
        InvitationForBid invitation = invitation();
        when(recordMapper.selectById(13)).thenReturn(record);
        when(invitationMapper.selectById(5)).thenReturn(invitation);
        when(recordMapper.markVectorizing(13)).thenReturn(1);
        when(client.replaceDocument(any(PreparedBidRecordDocument.class)))
                .thenReturn(1);
        when(recordMapper.markVectorized(eq(13), eq(1), any()))
                .thenReturn(1);
        BidRecordVectorIndexService service = service(
                recordMapper, invitationMapper, client);

        BidRecord indexed = service.reindex(13);

        assertEquals(record, indexed);
        ArgumentCaptor<PreparedBidRecordDocument> captor =
                ArgumentCaptor.forClass(PreparedBidRecordDocument.class);
        verify(client).replaceDocument(captor.capture());
        String content = captor.getValue().content();
        assertTrue(content.contains("投标记录 ID：13"));
        assertTrue(content.contains("投标人名称：测试投标人"));
        assertTrue(content.contains("项目编号：P-001"));
        assertTrue(content.contains("采购公司：测试采购公司"));
        assertTrue(content.contains("credential/converted.md"));
        verify(recordMapper).markVectorized(eq(13), eq(1), any());
    }

    @Test
    void shouldDeletePartialVectorAndMarkFailed() {
        BidRecordMapper recordMapper = mock(BidRecordMapper.class);
        InvitationForBidMapper invitationMapper = mock(InvitationForBidMapper.class);
        BidRecordVectorStorage client = mock(BidRecordVectorStorage.class);
        when(recordMapper.selectById(13)).thenReturn(record());
        when(invitationMapper.selectById(5)).thenReturn(invitation());
        when(recordMapper.markVectorizing(13)).thenReturn(1);
        when(client.replaceDocument(any(PreparedBidRecordDocument.class)))
                .thenThrow(new IllegalStateException("vector unavailable"));
        when(recordMapper.markVectorizeFailed(eq(13), any())).thenReturn(1);
        BidRecordVectorIndexService service = service(
                recordMapper, invitationMapper, client);

        try {
            service.reindex(13);
        } catch (IllegalStateException expected) {
            assertEquals("vector unavailable", expected.getMessage());
        }

        verify(client).deleteDocument(13);
        verify(recordMapper).markVectorizeFailed(13, "vector unavailable");
    }

    @Test
    void shouldContinueRefreshingInvitationRecordsAfterOneFailure() {
        BidRecordMapper recordMapper = mock(BidRecordMapper.class);
        InvitationForBidMapper invitationMapper = mock(InvitationForBidMapper.class);
        BidRecordVectorStorage client = mock(BidRecordVectorStorage.class);
        BidRecord first = record();
        BidRecord second = record();
        second.setBidRecordId(14);
        when(recordMapper.selectByInvitationId(5))
                .thenReturn(List.of(first, second));
        when(recordMapper.selectById(13)).thenReturn(first);
        when(recordMapper.selectById(14)).thenReturn(second);
        when(invitationMapper.selectById(5)).thenReturn(invitation());
        when(recordMapper.markVectorizing(anyInt())).thenReturn(1);
        when(client.replaceDocument(any(PreparedBidRecordDocument.class)))
                .thenThrow(new IllegalStateException("first failed"))
                .thenReturn(1);
        when(recordMapper.markVectorizeFailed(eq(13), any())).thenReturn(1);
        when(recordMapper.markVectorized(eq(14), eq(1), any())).thenReturn(1);
        BidRecordVectorIndexService service = service(
                recordMapper, invitationMapper, client);

        service.reindexByInvitationId(5);

        verify(recordMapper).markVectorizeFailed(13, "first failed");
        verify(recordMapper).markVectorized(eq(14), eq(1), any());
    }

    private BidRecordVectorIndexService service(
            BidRecordMapper recordMapper,
            InvitationForBidMapper invitationMapper,
            BidRecordVectorStorage client) {
        BidRecordVectorIndexService service = new BidRecordVectorIndexService();
        ReflectionTestUtils.setField(service, "bidRecordMapper", recordMapper);
        ReflectionTestUtils.setField(
                service, "invitationForBidMapper", invitationMapper);
        ReflectionTestUtils.setField(service, "vectorStorage", client);
        return service;
    }

    private BidRecord record() {
        BidRecord record = new BidRecord();
        record.setBidRecordId(13);
        record.setInvitationId(5);
        record.setBidNo(2);
        record.setBidAt(LocalDateTime.of(2026, 8, 7, 14, 56));
        record.setBidderName("测试投标人");
        record.setBidderAccountId(7L);
        record.setQuotePrice(new BigDecimal("19999999.00"));
        record.setBidderContact("199999999");
        record.setCredentials(List.of("credential/converted.md"));
        return record;
    }

    private InvitationForBid invitation() {
        InvitationForBid invitation = new InvitationForBid();
        invitation.setInvitationId(5);
        invitation.setProjectNo("P-001");
        invitation.setPurchaseTaskNo("T-001");
        invitation.setPurchaseCompany("测试采购公司");
        return invitation;
    }
}
