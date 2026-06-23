package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.service.BidWinningSupplierDetailService;
import org.apache.ibatis.annotations.Mapper;

/**
 * 中标结果明细 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code BidWinningSupplierDetailMapper.xml}。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseMapper&lt;BidWinningSupplierDetail&gt;
 *     ↑ extends
 *   BidWinningSupplierDetailMapper（本接口）— namespace = mapper/BidWinningSupplierDetailMapper.xml
 * </pre>
 *
 * <h3>对应数据库表 {@code bid_winning_supplier_detail}</h3>
 * <ul>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V5__create_bid_winning_supplier_detail.sql}</li>
 *   <li>主键：{@code detail_id}（自增），字段数：4</li>
 *   <li>逻辑外键：{@code notification_id} → {@code bid_winning_notification.notification_id}（1:N）</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidWinningSupplierDetail
 * @see BidWinningSupplierDetailService
 */
@Mapper
public interface BidWinningSupplierDetailMapper extends BaseMapper<BidWinningSupplierDetail> {
    // 继承自 BaseMapper
}
