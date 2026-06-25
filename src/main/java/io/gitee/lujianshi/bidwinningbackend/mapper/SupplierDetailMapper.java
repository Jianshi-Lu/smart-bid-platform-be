package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商明细 Mapper。继承 {@link BaseMapper}，绑定 {@code SupplierDetailMapper.xml}。
 */
@Mapper
public interface SupplierDetailMapper extends BaseMapper<SupplierDetail> {
}
