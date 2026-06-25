package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.TenderNotice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 招标公告 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code TenderNoticeMapper.xml}。
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.TenderNotice
 * @see TenderNoticeService
 */
@Mapper
public interface TenderNoticeMapper extends BaseMapper<TenderNotice> {
}
