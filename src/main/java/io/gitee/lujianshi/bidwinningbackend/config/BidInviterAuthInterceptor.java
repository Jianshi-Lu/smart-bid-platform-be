package io.gitee.lujianshi.bidwinningbackend.config;

import io.gitee.lujianshi.bidwinningbackend.service.BidInviterAccountService;
import io.gitee.lujianshi.bidwinningbackend.entity.bidinviteraccount.BidInviterAccount;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 招标方管理接口统一鉴权拦截器。
 *
 * <p>与投标方 Controller 内校验对称：每个匹配请求先通过
 * {@link BidInviterAccountService#authenticateBasic} 校验 HTTP Basic 凭据，
 * 未登录或凭据错误直接抛 401 业务异常。</p>
 *
 * @since V39
 */
@Component
public class BidInviterAuthInterceptor implements HandlerInterceptor {

    @Resource
    private BidInviterAccountService bidInviterAccountService;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        BidInviterAccount account = bidInviterAccountService.authenticateBasic(
                request.getHeader("Authorization"));
        CurrentAccountContext.set(new CurrentAccountContext.CurrentAccount(
                CurrentAccountContext.BID_INVITER,
                account.getBidInviterAccountId(),
                account.getUsername()));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        CurrentAccountContext.clear();
    }
}
