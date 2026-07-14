package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.InvitationForBid;
import org.apache.ibatis.annotations.Mapper;

/**
 * 招标公告 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code InvitationForBidMapper.xml}（V18 重命名自 TenderNoticeMapper）。
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.InvitationForBid
 * @see InvitationForBidService
 */
@Mapper
public interface InvitationForBidMapper extends BaseMapper<InvitationForBid, Integer> {
}
