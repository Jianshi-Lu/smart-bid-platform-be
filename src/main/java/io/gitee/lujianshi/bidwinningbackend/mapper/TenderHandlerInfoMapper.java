package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.TenderHandlerInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 招标经办人 Mapper。继承 {@link BaseMapper}，绑定 {@code TenderHandlerInfoMapper.xml}。
 */
@Mapper
public interface TenderHandlerInfoMapper extends BaseMapper<TenderHandlerInfo> {
}
