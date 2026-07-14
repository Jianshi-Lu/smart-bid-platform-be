package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.InvitationForBidStatusHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 招标公告状态变更历史 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code InvitationForBidStatusHistoryMapper.xml}（V18 重命名自 TenderNoticeStatusHistoryMapper）。
 */
@Mapper
public interface InvitationForBidStatusHistoryMapper extends BaseMapper<InvitationForBidStatusHistory, Integer> {
}
