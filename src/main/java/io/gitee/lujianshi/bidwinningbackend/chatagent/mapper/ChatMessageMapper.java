package io.gitee.lujianshi.bidwinningbackend.chatagent.mapper;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatMessage;
import io.gitee.lujianshi.bidwinningbackend.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** chat-agent 消息 Mapper，绑定独立 chatAgentSqlSessionFactory。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage, String> {

    /** 按会话查询可见消息，并按会话内序号升序返回。 */
    List<ChatMessage> selectByConversationId(
            @Param("conversationId") String conversationId);
}
