package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.controller.BidHandlerInfoController;
import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.service.BidHandlerInfoService;
import org.apache.ibatis.annotations.Mapper;

/**
 * 经办人 Mapper 接口 — 继承 {@link BaseMapper}，绑定 {@code BidHandlerInfoMapper.xml}。
 *
 * <h3>继承链路</h3>
 * <pre>
 *   BaseMapper&lt;BidHandlerInfo&gt;
 *     ↑ extends
 *   BidHandlerInfoMapper（本接口）— namespace = mapper/BidHandlerInfoMapper.xml
 * </pre>
 *
 * <h3>对应数据库表 {@code bid_handler_info}</h3>
 * <ul>
 *   <li>建表脚本：{@code src/main/resources/sql/ddl/V1~V3__*.sql}</li>
 *   <li>主键：{@code handler_id}（自增），字段数：4</li>
 * </ul>
 *
 * @see io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo
 * @see BidHandlerInfoService
 * @see BidHandlerInfoController
 */
@Mapper
public interface BidHandlerInfoMapper extends BaseMapper<BidHandlerInfo> {
    // 继承自 BaseMapper
}
