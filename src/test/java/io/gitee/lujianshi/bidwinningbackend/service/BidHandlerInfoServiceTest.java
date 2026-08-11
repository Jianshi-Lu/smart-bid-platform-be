package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.entity.bidhandler.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.enums.OperationType;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.InvitationForBidMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidHandlerInfoServiceTest {

    @Test
    void shouldRejectDeletingHandlerStillReferencedByInvitation() {
        BidHandlerInfoService service = new BidHandlerInfoService();
        InvitationForBidMapper invitationForBidMapper = mock(InvitationForBidMapper.class);
        when(invitationForBidMapper.countByBidHandlerId(7)).thenReturn(1);
        ReflectionTestUtils.setField(service, "invitationForBidMapper", invitationForBidMapper);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.delete(7));

        assertEquals(AppErrorCode.BID_HANDLER_HAS_INVITATIONS, exception.getErrorCode());
        assertEquals("CHECK_INVITATIONS", exception.getStage());
    }

    @Test
    void shouldRecordDeleteHistoryAfterDeletingUnusedHandler() {
        BidHandlerInfoService service = new BidHandlerInfoService();
        BidHandlerInfoMapper bidHandlerInfoMapper = mock(BidHandlerInfoMapper.class);
        InvitationForBidMapper invitationForBidMapper = mock(InvitationForBidMapper.class);
        OperationHistoryService operationHistoryService = mock(OperationHistoryService.class);
        BidHandlerInfo handler = new BidHandlerInfo();
        handler.setBidHandlerId(7);
        handler.setHandlerName("DELETE-TEST");
        when(invitationForBidMapper.countByBidHandlerId(7)).thenReturn(0);
        when(bidHandlerInfoMapper.selectById(7)).thenReturn(handler);
        when(bidHandlerInfoMapper.deleteById(7)).thenReturn(1);
        ReflectionTestUtils.setField(service, "mapper", bidHandlerInfoMapper);
        ReflectionTestUtils.setField(service, "invitationForBidMapper", invitationForBidMapper);
        ReflectionTestUtils.setField(service, "operationHistoryService", operationHistoryService);

        int deleted = service.delete(7);

        assertEquals(1, deleted);
        verify(operationHistoryService).recordBidHandler(
                7,
                OperationType.DELETE,
                handler,
                null,
                null);
    }
}
