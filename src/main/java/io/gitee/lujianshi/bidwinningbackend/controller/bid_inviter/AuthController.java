package io.gitee.lujianshi.bidwinningbackend.controller.bid_inviter;

import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.dto.bidinviteraccount.LoginRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidinviteraccount.LoginResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.bidinviteraccount.RegisterRequest;
import io.gitee.lujianshi.bidwinningbackend.entity.BidInviterAccount;
import io.gitee.lujianshi.bidwinningbackend.service.BidInviterAccountService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 招标方账号认证入口（登录/注册/当前账号）。
 *
 * <p>与投标方对称：注册/登录不需要鉴权；查询当前账号通过 HTTP Basic 凭据实时校验。
 * 招标方管理接口由 {@code BidInviterAuthInterceptor} 统一校验该账号。</p>
 *
 * @since V31
 */
@RestController("bidInviterAuthController")
@RequestMapping("/bid-inviter/auth")
public class AuthController {

    @Resource
    private BidInviterAccountService bidInviterAccountService;

    /** 注册招标方账号。 */
    @PostMapping("/register")
    public Result<BidInviterAccount> register(
            @Valid @RequestBody RegisterRequest request) {
        BidInviterAccount account = bidInviterAccountService.register(request);
        return Result.success(account, "注册成功");
    }

    /** 登录并返回账号信息。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return Result.success(
                bidInviterAccountService.login(request), "登录成功");
    }

    /** 查询当前登录账号。 */
    @GetMapping("/me")
    public Result<LoginResponse> me(HttpServletRequest request) {
        BidInviterAccount account = bidInviterAccountService.authenticateBasic(
                request.getHeader("Authorization"));
        return Result.success(
                new LoginResponse(
                        account.getBidInviterAccountId(),
                        account.getUsername(),
                        account.getDisplayName()),
                "当前登录账号");
    }
}
