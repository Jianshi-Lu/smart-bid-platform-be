package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.bidderaccount.BidderAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


/**
 * 投标方账号 Mapper。继承 {@link BaseMapper}，绑定 {@code BidderAccountMapper.xml}。
 *
 * <p>V36 新增：账号注册/登录所需的查询。</p>
 *
 * @since V36
 */
@Mapper
public interface BidderAccountMapper extends BaseMapper<BidderAccount, Long> {

    /** 按登录账号名查询（账号名唯一）。 */
    BidderAccount selectByUsername(@Param("username") String username);

}
