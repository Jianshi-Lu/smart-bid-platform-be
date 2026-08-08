package io.gitee.lujianshi.bidwinningbackend.dto.bidderaccount;

/**
 * 登录成功响应。
 *
 * <p>最小鉴权实现：不签发令牌，客户端后续请求直接携带用户名+密码
 * （HTTP Basic），后端每次校验。{@code /bidder/auth/me} 返回同一结构。</p>
 *
 * @since V36
 */
public record LoginResponse(
        Long bidderAccountId,
        String username,
        String displayName) {
}
