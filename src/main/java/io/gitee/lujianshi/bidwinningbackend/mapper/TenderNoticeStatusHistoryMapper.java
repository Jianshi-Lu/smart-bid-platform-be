package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.TenderNoticeStatusHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 招标公告状态变更历史 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code TenderNoticeStatusHistoryMapper.xml}。
 */
@Mapper
public interface TenderNoticeStatusHistoryMapper extends BaseMapper<TenderNoticeStatusHistory> {
}
