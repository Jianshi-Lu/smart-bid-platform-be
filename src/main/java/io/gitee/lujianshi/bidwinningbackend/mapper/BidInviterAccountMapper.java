package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.BidInviterAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 招标方账号 Mapper。继承 {@link BaseMapper}，绑定 {@code BidInviterAccountMapper.xml}。
 *
 * @since V31
 */
@Mapper
public interface BidInviterAccountMapper extends BaseMapper<BidInviterAccount, Long> {

    /** 按登录账号名查询（账号名唯一）。 */
    BidInviterAccount selectByUsername(@Param("username") String username);
}
