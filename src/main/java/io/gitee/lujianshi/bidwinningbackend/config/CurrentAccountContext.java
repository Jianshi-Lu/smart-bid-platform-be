package io.gitee.lujianshi.bidwinningbackend.config;

/**
 * 当前请求操作人上下文（ThreadLocal）。
 *
 * <p>招标方管理接口由 {@link BidInviterAuthInterceptor} 在鉴权通过后写入；
 * 投标方接口由 {@code controller.bidder.BidRecordController} 在校验账号后写入。
 * 操作历史服务在写历史时读取该上下文，不向业务方法传递账号参数。</p>
 *
 * @since V33
 */
public final class CurrentAccountContext {

    /** 招标方（管理员）账号。 */
    public static final String BID_INVITER = "BID_INVITER";

    /** 投标方账号。 */
    public static final String BIDDER = "BIDDER";

    private static final ThreadLocal<CurrentAccount> HOLDER = new ThreadLocal<>();

    private CurrentAccountContext() {
    }

    /** 当前操作人：类型 + 账号 ID + 登录用户名（对象存储路径归属用）。 */
    public record CurrentAccount(
            String operatorType,
            Long operatorId,
            String username) {
    }

    public static void set(CurrentAccount account) {
        HOLDER.set(account);
    }

    /** 返回当前操作人；无鉴权上下文（如系统内部任务）时返回 null。 */
    public static CurrentAccount get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
