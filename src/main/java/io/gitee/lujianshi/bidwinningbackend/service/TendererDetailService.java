package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.entity.TendererDetail;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import io.gitee.lujianshi.bidwinningbackend.mapper.TendererDetailMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * 投标人明细 Service。继承 {@link BaseService}，无业务方法重写。
 */
@Service
public class TendererDetailService extends BaseService<TendererDetail> {

    @Resource
    private TendererDetailMapper mapper;

    @Override
    protected BaseMapper<TendererDetail> getMapper() {
        return mapper;
    }

    @Override
    protected Function<TendererDetail, Integer> getIdExtractor() {
        return TendererDetail::getTendererId;
    }
}
