package io.gitee.lujianshi.bidwinningbackend.service;

import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;
import io.gitee.lujianshi.bidwinningbackend.mapper.BidHandlerInfoMapper;

import java.util.List;

public class BidHandlerInfoService {
    private BidHandlerInfoMapper mapper;

    public int create(BidHandlerInfo entity) {
        return mapper.insert(entity);
    }

    public int update(BidHandlerInfo entity) {
        return mapper.updateById(entity);
    }

    public int delete(Integer id) {
        return mapper.deleteById(id);
    }

    public BidHandlerInfo getById(Integer id) {
        return mapper.selectById(id);
    }

    public List<BidHandlerInfo> listAll() {
        return mapper.selectAll();
    }
}
