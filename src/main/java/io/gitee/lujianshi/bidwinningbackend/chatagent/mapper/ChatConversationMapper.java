package io.gitee.lujianshi.bidwinningbackend.chatagent.mapper;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatConversation;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** chat-agent 对话 Mapper，绑定独立 chatAgentSqlSessionFactory。 */
@Mapper
public interface ChatConversationMapper
        extends BaseMapper<ChatConversation, String> {

    /** 按用户查询未删除会话，供后续会话列表 API 使用。 */
    List<ChatConversation> selectByUserId(@Param("userId") Long userId);
}
