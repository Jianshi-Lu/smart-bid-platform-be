package io.gitee.lujianshi.bidwinningbackend.controller.bidder;

import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.InvitationForBidService;
import io.gitee.lujianshi.bidwinningbackend.service.account.BidderAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationControllerTest {

    @Mock
    private InvitationForBidService invitationForBidService;

    @Mock
    private BidderAccountService bidderAccountService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private InvitationController controller;

    @Test
    void authenticatesBidderBeforeListingInvitations() {
        when(request.getHeader("Authorization")).thenReturn("Basic bidder");
        when(invitationForBidService.listAll()).thenReturn(List.of());

        assertEquals(0, controller.list(
                1, 10, "invitation_id desc", request).getData().getTotal());

        verify(bidderAccountService).authenticateBasic("Basic bidder");
        verify(invitationForBidService).listAll();
    }

    @Test
    void authenticatesBidderBeforeReadingInvitation() {
        InvitationForBid invitation = new InvitationForBid();
        invitation.setInvitationId(6);
        when(request.getHeader("Authorization")).thenReturn("Basic bidder");
        when(invitationForBidService.getById(6))
                .thenReturn(invitation);

        assertEquals(6, controller.get(6, request).getData().getInvitationId());

        verify(bidderAccountService).authenticateBasic("Basic bidder");
        verify(invitationForBidService).getById(6);
    }

    @Test
    void rejectsMissingCredentialsBeforeReadingBusinessData() {
        AppException failure = new AppException(
                AppErrorCode.BIDDER_ACCOUNT_UNAUTHORIZED,
                "bidder-account",
                "AUTH",
                "RESOLVE_CREDENTIALS",
                "用户名或密码错误");
        when(bidderAccountService.authenticateBasic(null)).thenThrow(failure);

        assertThrows(AppException.class,
                () -> controller.list(1, 10, "invitation_id desc", request));

        verify(invitationForBidService, never()).listAll();
    }
}
