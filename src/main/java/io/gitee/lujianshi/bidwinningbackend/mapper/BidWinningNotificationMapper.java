package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定标通知 Mapper。继承 {@link BaseMapper}，绑定 {@code BidWinningNotificationMapper.xml}。
 */
@Mapper
public interface BidWinningNotificationMapper extends BaseMapper<BidWinningNotification> {
}
