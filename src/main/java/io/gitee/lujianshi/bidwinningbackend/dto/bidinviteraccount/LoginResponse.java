package io.gitee.lujianshi.bidwinningbackend.dto.bidinviteraccount;

/**
 * 招标方登录成功响应。
 *
 * <p>与投标方对称：不签发令牌，客户端后续请求携带 HTTP Basic，
 * 后端每次实时校验。{@code /bid-inviter/auth/me} 返回同一结构。</p>
 *
 * @since V31
 */
public record LoginResponse(
        Long bidInviterAccountId,
        String username,
        String displayName) {
}
