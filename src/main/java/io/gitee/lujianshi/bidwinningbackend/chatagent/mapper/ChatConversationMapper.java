package io.gitee.lujianshi.bidwinningbackend.chatagent.mapper;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatConversation;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** chat-agent 对话 Mapper，绑定独立 chatAgentSqlSessionFactory。 */
@Mapper
public interface ChatConversationMapper
        extends BaseMapper<ChatConversation, String> {

    /** 按用户查询未删除会话，供后续会话列表 API 使用。 */
    List<ChatConversation> selectByUserId(@Param("userId") Long userId);

    /** 锁定会话行，用于数据库层面的序号分配保护。 */
    ChatConversation selectByIdForUpdate(@Param("conversationId") String conversationId);

    /** 仅在标题仍为自动来源时更新标题，避免覆盖用户手动修改。 */
    int updateTitleIfAuto(
            @Param("conversationId") String conversationId,
            @Param("title") String title);

    /** 手动修改标题，并把来源标记为 MANUAL。 */
    int updateTitleManual(
            @Param("conversationId") String conversationId,
            @Param("title") String title);

    /** 软删除会话；已删除的会话保持幂等，不重复更新。 */
    int softDeleteById(
            @Param("conversationId") String conversationId,
            @Param("deletedAt") LocalDateTime deletedAt);
}
