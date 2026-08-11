package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid;
import io.gitee.lujianshi.bidwinningbackend.enums.BidNoticeStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 招标公告 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code InvitationForBidMapper.xml}（V18 重命名自 TenderNoticeMapper）。
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBid
 * @see io.gitee.lujianshi.bidwinningbackend.service.invitation.InvitationForBidService
 */
@Mapper
public interface InvitationForBidMapper extends BaseMapper<InvitationForBid, Integer> {

    /** 统计仍引用某经办人的公告数，用于拒绝会产生逻辑外键悬空的经办人删除。 */
    int countByBidHandlerId(@Param("bidHandlerId") Integer bidHandlerId);

    /** 仅当公告仍处于预期原状态时，原子更新为目标状态。 */
    int updateStatusIfCurrent(
            @Param("invitationId") Integer invitationId,
            @Param("fromStatus") BidNoticeStatus fromStatus,
            @Param("toStatus") BidNoticeStatus toStatus);
}
