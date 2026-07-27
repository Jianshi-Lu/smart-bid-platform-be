package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;

/** 招标公告状态已被其他请求修改，当前状态变更未获得更新权。 */
public class InvitationStatusChangeConflictException extends RuntimeException {

    public InvitationStatusChangeConflictException(
            Integer invitationId,
            BidNoticeStatus expectedStatus) {
        super("招标公告状态已发生变化，请刷新后重试: invitationId="
                + invitationId + ", expectedStatus=" + expectedStatus);
    }
}
