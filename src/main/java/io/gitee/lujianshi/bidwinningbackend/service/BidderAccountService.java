package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.LoginRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.LoginResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.RegisterRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidderAccountMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 投标方账号服务：注册、登录与明文密码校验（演示环境最小实现）。
 *
 * <p>最小鉴权实现：每次请求携带用户名+密码，服务端直接校验，不签发令牌。
 * 演示环境明文存储密码（V29），禁止用于生产。</p>
 *
 * @since V28
 */
@Service
public class BidderAccountService {

    @Resource
    private BidderAccountMapper bidderAccountMapper;

    /**
     * 注册投标方账号。用户名唯一，密码 6-64 位。
     *
     * @param request 注册请求
     * @return 已创建账号（不含密码哈希明文）
     * @throws AppException 用户名已存在或参数不合法
     */
    @Transactional(rollbackFor = Exception.class)
    public BidderAccount register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String password = request.getPassword();
        if (username.isBlank()) {
            throw invalidArgument("注册账号名不能为空");
        }
        if (password == null || password.length() < 6 || password.length() > 64) {
            throw invalidArgument("密码长度需在6-64之间");
        }
        if (bidderAccountMapper.selectByUsername(username) != null) {
            throw new AppException(
                    AppErrorCode.BIDDER_ACCOUNT_USERNAME_TAKEN,
                    "bidder-account",
                    "REGISTER",
                    "CHECK_USERNAME",
                    "账号名已存在");
        }

        BidderAccount account = new BidderAccount();
        account.setUsername(username);
        account.setPassword(password);
        account.setPhone(blankToNull(request.getPhone()));
        account.setDisplayName(blankToNull(request.getDisplayName()));
        account.setCreatedAt(LocalDateTime.now());
        bidderAccountMapper.insert(account);

        // 返回给调用方的对象不携带密码哈希，避免序列化泄露。
        BidderAccount created = new BidderAccount();
        created.setBidderAccountId(account.getBidderAccountId());
        created.setUsername(account.getUsername());
        created.setPhone(account.getPhone());
        created.setDisplayName(account.getDisplayName());
        created.setCreatedAt(account.getCreatedAt());
        return created;
    }

    /**
     * 登录：校验用户名与密码。
     *
     * @param request 登录请求
     * @return 登录账号信息
     * @throws AppException 账号或密码错误
     */
    public LoginResponse login(LoginRequest request) {
        BidderAccount account = authenticate(
                request.getUsername().trim(), request.getPassword());
        return new LoginResponse(
                account.getBidderAccountId(),
                account.getUsername(),
                account.getDisplayName());
    }

    /**
     * 校验用户名与密码并返回账号，用于请求鉴权（HTTP Basic）。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 匹配的账号
     * @throws AppException 账号或密码错误
     */
    public BidderAccount authenticate(String username, String password) {
        BidderAccount account = bidderAccountMapper.selectByUsername(username);
        if (account == null
                || !password.equals(account.getPassword())) {
            throw new AppException(
                    AppErrorCode.BIDDER_ACCOUNT_INVALID_CREDENTIALS,
                    "bidder-account",
                    "LOGIN",
                    "VERIFY_PASSWORD",
                    "账号或密码错误");
        }
        return account;
    }

    /**
     * 从 HTTP Basic 请求头解析并校验账号，供受保护接口直接调用。
     *
     * @param authorizationHeader {@code Authorization: Basic base64(username:password)}
     * @return 匹配的账号
     * @throws AppException 请求头缺失/格式错误或账号密码不匹配
     */
    public BidderAccount authenticateBasic(String authorizationHeader) {
        String[] credentials = resolveCredentials(authorizationHeader);
        return authenticate(credentials[0], credentials[1]);
    }

    /** 解析 Basic 认证头，返回 [username, password]；缺失或格式错误时抛出认证异常。 */
    private String[] resolveCredentials(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Basic ")) {
            throw unauthorized();
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(
                            authorizationHeader.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0 || separator == decoded.length() - 1) {
                throw unauthorized();
            }
            return new String[] {
                    decoded.substring(0, separator),
                    decoded.substring(separator + 1)
            };
        } catch (IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    private AppException unauthorized() {
        return new AppException(
                AppErrorCode.BIDDER_ACCOUNT_UNAUTHORIZED,
                "bidder-account",
                "AUTH",
                "RESOLVE_CREDENTIALS",
                "用户名或密码错误");
    }

    private AppException invalidArgument(String message) {
        return new AppException(
                AppErrorCode.COMMON_INVALID_ARGUMENT,
                "bidder-account",
                "REGISTER",
                "VALIDATE_REQUEST",
                message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
