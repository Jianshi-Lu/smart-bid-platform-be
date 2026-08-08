package io.gitee.lujianshi.bidwinningbackend.enums;

import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;

/**
 * 招标公告状态枚举（V18 重命名自 TenderNoticeStatus）。
 *
 * <p>状态机（线性单向 + 任意态可终止于 {@link #CANCELLED}）：
 * <pre>
 *   DRAFT  →  PUBLISHED  →  CLOSED  →  AWARDED
 *      │         │             │         │
 *      └─────────┴─────────────┴─────────┴──▶  CANCELLED（终态）
 * </pre>
 * </p>
 *
 * <p>合法跳转规则（{@link #canTransitionTo}）：
 * <ul>
 *   <li>不可自转（from == to 视为非法）</li>
 *   <li>{@link #CANCELLED} 为终态，不可再转</li>
 *   <li>任意非 CANCELLED 状态可转 CANCELLED</li>
 *   <li>线性推进：DRAFT→PUBLISHED→CLOSED→AWARDED（ordinal 差 1）</li>
 * </ul>
 * </p>
 *
 * @see InvitationForBid
 * @since V18
 */
public enum BidNoticeStatus {

    /** 草案 */
    DRAFT,

    /** 已发布 */
    PUBLISHED,

    /** 已截止（报名/投标截止） */
    CLOSED,

    /** 已定标 */
    AWARDED,

    /** 已取消（终态） */
    CANCELLED;

    /**
     * 判断当前状态到目标状态是否为合法跳转。
     *
     * @param to 目标状态
     * @return true = 合法，false = 非法
     */
    public boolean canTransitionTo(BidNoticeStatus to) {
        if (to == null || to == this) {
            return false;
        }
        if (this == CANCELLED) {
            return false;
        }
        if (to == CANCELLED) {
            return true;
        }
        return to.ordinal() == this.ordinal() + 1;
    }
}
