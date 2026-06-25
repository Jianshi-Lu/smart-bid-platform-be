package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.TendererDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 投标人明细 Mapper。继承 {@link BaseMapper}，绑定 {@code TendererDetailMapper.xml}。
 */
@Mapper
public interface TendererDetailMapper extends BaseMapper<TendererDetail> {
}
