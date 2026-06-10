package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidHandlerInfo;

public interface BidHandlerInfoMapper {

    int insert(BidHandlerInfo entity);

    int updateById(BidHandlerInfo entity);

    int deleteById(Integer handlerId);

    BidHandlerInfo selectById(Integer handlerId);

    java.util.List<BidHandlerInfo> selectAll();
}