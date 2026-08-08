package io.gitee.lujianshi.bidwinningbackend.mapper;

import io.gitee.lujianshi.bidwinningbackend.entity.invitationforbid.InvitationForBidOperationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 招标公告操作历史 Mapper（V33）。继承 {@link OperationHistoryBaseMapper}，绑定
 * {@code InvitationForBidOperationHistoryMapper.xml}。
 *
 * @since V33
 */
@Mapper
public interface InvitationForBidOperationHistoryMapper
        extends OperationHistoryBaseMapper<InvitationForBidOperationHistory> {

    /** 按公告 ID 查询操作历史（时间倒序）。 */
    List<InvitationForBidOperationHistory> selectByInvitationId(
            @Param("invitationId") Integer invitationId);
}
