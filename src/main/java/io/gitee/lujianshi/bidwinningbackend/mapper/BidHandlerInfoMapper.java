package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 经办人 Mapper。继承 {@link BaseMapper}，绑定 {@code BidHandlerInfoMapper.xml}。
 */
@Mapper
public interface BidHandlerInfoMapper extends BaseMapper<BidHandlerInfo> {
}
