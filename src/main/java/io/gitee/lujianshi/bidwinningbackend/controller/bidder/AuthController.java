package io.gitee.lujianshi.bidwinningbackend.controller.bidder;

import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.LoginRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.LoginResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount.RegisterRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.entity.BidderAccount;
import io.gitee.lujianshi.bidwinningbackend.service.BidderAccountService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投标方账号认证入口（登录/注册/当前账号）。
 *
 * <p>注册/登录不需要鉴权；查询当前账号通过 HTTP Basic 凭据实时校验。
 * 最小鉴权实现不提供服务端登出，受保护接口统一调用
 * {@link BidderAccountService#authenticateBasic} 校验。</p>
 *
 * @since V28
 */
@RestController
@RequestMapping("/bidder/auth")
public class AuthController {

    @Resource
    private BidderAccountService bidderAccountService;

    /** 注册投标方账号。 */
    @PostMapping("/register")
    public Result<BidderAccount> register(
            @Valid @RequestBody RegisterRequest request) {
        BidderAccount account = bidderAccountService.register(request);
        return Result.success(account, "注册成功");
    }

    /** 登录并返回账号信息。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return Result.success(
                bidderAccountService.login(request), "登录成功");
    }

    /** 查询当前登录账号。 */
    @GetMapping("/me")
    public Result<LoginResponse> me(HttpServletRequest request) {
        BidderAccount account = bidderAccountService.authenticateBasic(
                request.getHeader("Authorization"));
        return Result.success(
                new LoginResponse(
                        account.getBidderAccountId(),
                        account.getUsername(),
                        account.getDisplayName()),
                "当前登录账号");
    }
}
