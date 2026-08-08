package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.bidhandler.BidHandlerInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 招标经办人 Mapper。继承 {@link BaseMapper}，绑定 {@code BidHandlerInfoMapper.xml}（V18 重命名自 TenderHandlerInfoMapper）。
 */
@Mapper
public interface BidHandlerInfoMapper extends BaseMapper<BidHandlerInfo, Integer> {
}
