package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定标通知 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code BidWinningNotificationMapper.xml}。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseMapper&lt;BidWinningNotification&gt;
 *     ↑ extends
 *   BidWinningNotificationMapper（本接口）— namespace = mapper/BidWinningNotificationMapper.xml
 * </pre>
 *
 * <h3>对应数据库表 {@code bid_winning_notification}</h3>
 * <ul>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V4__create_bid_winning_notification.sql}</li>
 *   <li>主键：{@code notification_id}（自增），字段数：19</li>
 *   <li>逻辑外键：{@code handler_id} → {@code bid_handler_info.handler_id}</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningNotification
 * @see io.gitee.lujianshi.bidwinningbackend.service.bidwinningnotification.BidWinningNotificationService
 */
@Mapper
public interface BidWinningNotificationMapper extends BaseMapper<BidWinningNotification> {
    // 继承自 BaseMapper
}
