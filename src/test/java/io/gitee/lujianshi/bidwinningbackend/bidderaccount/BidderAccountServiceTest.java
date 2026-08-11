package io.gitee.lujianshi.bidwinningbackend.bidderaccount;

import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.LoginRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.LoginResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.RegisterRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidderAccountMapper;
import io.gitee.lujianshi.bidwinningbackend.service.account.BidderAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 投标方账号服务：注册、登录与明文密码校验（演示环境最小实现）。 */
class BidderAccountServiceTest {

    private BidderAccountMapper mapper;
    private BidderAccountService service;

    @BeforeEach
    void setUp() {
        mapper = mock(BidderAccountMapper.class);
        service = new BidderAccountService();
        ReflectionTestUtils.setField(service, "bidderAccountMapper", mapper);
    }

    @Test
    void shouldRegisterAccountWithPlaintextPassword() {
        when(mapper.selectByUsername("alice")).thenReturn(null);
        when(mapper.insert(any(BidderAccount.class))).thenReturn(1);

        RegisterRequest request = new RegisterRequest();
        request.setUsername(" alice ");
        request.setPassword("secret123");
        request.setPhone("13800000000");
        request.setDisplayName("测试投标方");

        BidderAccount account = service.register(request);

        assertEquals("alice", account.getUsername());
        // 返回给调用方的实体不暴露密码。
        assertEquals(null, account.getPassword());

        ArgumentCaptor<BidderAccount> captor =
                ArgumentCaptor.forClass(BidderAccount.class);
        verify(mapper).insert(captor.capture());
        assertEquals("secret123", captor.getValue().getPassword());
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(mapper.selectByUsername("alice"))
                .thenReturn(new BidderAccount());
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret123");

        AppException exception = assertThrows(
                AppException.class, () -> service.register(request));
        assertEquals(
                "BIDDER_ACCOUNT.USERNAME_TAKEN",
                exception.getErrorCode().getCode());
    }

    @Test
    void shouldLoginWithValidCredentials() {
        BidderAccount stored = account("alice", "secret123");
        when(mapper.selectByUsername("alice")).thenReturn(stored);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret123");

        LoginResponse response = service.login(request);

        assertEquals(1L, response.bidderAccountId());
        assertEquals("alice", response.username());
        verify(mapper).selectByUsername("alice");
    }

    @Test
    void shouldRejectWrongPassword() {
        when(mapper.selectByUsername("alice"))
                .thenReturn(account("alice", "secret123"));
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong-password");

        AppException exception = assertThrows(
                AppException.class, () -> service.login(request));
        assertEquals(
                "BIDDER_ACCOUNT.INVALID_CREDENTIALS",
                exception.getErrorCode().getCode());
    }

    @Test
    void shouldAuthenticateWithValidCredentials() {
        BidderAccount stored = account("alice", "secret123");
        when(mapper.selectByUsername("alice")).thenReturn(stored);

        assertEquals(
                "alice",
                service.authenticate("alice", "secret123").getUsername());

        assertThrows(
                AppException.class,
                () -> service.authenticate("alice", "wrong-password"));
        assertThrows(
                AppException.class,
                () -> service.authenticate("missing", "secret123"));
    }

    @Test
    void shouldAuthenticateFromBasicHeader() {
        when(mapper.selectByUsername("alice"))
                .thenReturn(account("alice", "secret123"));
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                "alice:secret123".getBytes(StandardCharsets.UTF_8));

        assertEquals(
                "alice",
                service.authenticateBasic(basic).getUsername());
    }

    @Test
    void shouldRejectMalformedBasicHeader() {
        assertThrows(
                AppException.class,
                () -> service.authenticateBasic(null));
        assertThrows(
                AppException.class,
                () -> service.authenticateBasic("Bearer token"));
        assertThrows(
                AppException.class,
                () -> service.authenticateBasic("Basic !!!not-base64!!!"));
        assertThrows(
                AppException.class,
                () -> service.authenticateBasic("Basic " + Base64.getEncoder()
                        .encodeToString("no-colon".getBytes(StandardCharsets.UTF_8))));
    }

    private BidderAccount account(String username, String password) {
        BidderAccount account = new BidderAccount();
        account.setBidderAccountId(1L);
        account.setUsername(username);
        account.setPassword(password);
        return account;
    }
}
