package io.gitee.lujianshi.bidwinningbackend.config;

import io.gitee.lujianshi.bidwinningbackend.entity.bidinviteraccount.BidInviterAccount;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.service.account.BidInviterAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidInviterAuthInterceptorTest {

    @Mock
    private BidInviterAccountService bidInviterAccountService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private BidInviterAuthInterceptor interceptor;

    @AfterEach
    void clearCurrentAccount() {
        CurrentAccountContext.clear();
    }

    @Test
    void authenticatesEveryMatchedRequestAndSetsInviterContext() {
        BidInviterAccount account = new BidInviterAccount();
        account.setBidInviterAccountId(1L);
        account.setUsername("admin");
        when(request.getHeader("Authorization")).thenReturn("Basic credentials");
        when(bidInviterAccountService.authenticateBasic("Basic credentials"))
                .thenReturn(account);

        assertTrue(interceptor.preHandle(request, response, new Object()));

        verify(bidInviterAccountService).authenticateBasic("Basic credentials");
        assertEquals(CurrentAccountContext.BID_INVITER,
                CurrentAccountContext.get().operatorType());
        assertEquals(1L, CurrentAccountContext.get().operatorId());
        assertEquals("admin", CurrentAccountContext.get().username());
    }

    @Test
    void rejectsMatchedRequestWhenCredentialsAreMissing() {
        AppException failure = new AppException(
                AppErrorCode.BID_INVITER_ACCOUNT_UNAUTHORIZED,
                "bid-inviter-account",
                "AUTH",
                "RESOLVE_CREDENTIALS",
                "用户名或密码错误");
        when(bidInviterAccountService.authenticateBasic(null))
                .thenThrow(failure);

        assertThrows(AppException.class,
                () -> interceptor.preHandle(request, response, new Object()));

        verify(bidInviterAccountService).authenticateBasic(null);
    }

    @Test
    void clearsCurrentAccountAfterRequestCompletion() {
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BID_INVITER, 1L, "admin"));

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(CurrentAccountContext.get());
    }
}
