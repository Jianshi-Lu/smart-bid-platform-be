package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.entity.SupplierDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.SupplierDetailMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * 供应商明细 Service。继承 {@link BaseService}，无业务方法重写。
 */
@Service
public class SupplierDetailService extends BaseService<SupplierDetail> {

    @Resource
    private SupplierDetailMapper mapper;

    @Override
    protected BaseMapper<SupplierDetail> getMapper() {
        return mapper;
    }

    @Override
    protected Function<SupplierDetail, Integer> getIdExtractor() {
        return SupplierDetail::getSupplierId;
    }
}
