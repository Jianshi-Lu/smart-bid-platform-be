package io.gitee.lujianshi.bidwinningbackend.chatagent;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatConversation;
import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatMessage;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatConversationMapper;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 验证聊天 Mapper 实际连接 chat_agent，并能够读写两张聊天表。 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(
        named = "chat-agent.integration-test",
        matches = "true")
@Transactional(transactionManager = "chatAgentTransactionManager")
class ChatPersistenceIntegrationTest {

    @Autowired
    private ChatConversationMapper conversationMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Autowired
    @Qualifier("chatAgentDataSource")
    private DataSource chatAgentDataSource;

    @Test
    void shouldPersistConversationAndOrderedMessagesInChatAgent() {
        String conversationId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();

        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setUserId(1L);
        conversation.setTitle("持久化验证");
        conversation.setNextSequenceNo(1L);
        conversation.setStatus("ACTIVE");

        assertEquals(1, conversationMapper.insert(conversation));

        ChatMessage userMessage = new ChatMessage();
        userMessage.setMessageId(UUID.randomUUID().toString());
        userMessage.setConversationId(conversationId);
        userMessage.setRequestId(requestId);
        userMessage.setClientMessageId(UUID.randomUUID().toString());
        userMessage.setRole("USER");
        userMessage.setContent("第一个问题");
        userMessage.setContentType("TEXT");
        userMessage.setSequenceNo(1L);
        userMessage.setStatus("COMPLETED");

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setMessageId(UUID.randomUUID().toString());
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRequestId(requestId);
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setContent("第一个回答");
        assistantMessage.setContentType("TEXT");
        assistantMessage.setSequenceNo(2L);
        assistantMessage.setStatus("COMPLETED");

        assertEquals(1, messageMapper.insert(userMessage));
        assertEquals(1, messageMapper.insert(assistantMessage));

        ChatConversation savedConversation =
                conversationMapper.selectById(conversationId);
        List<ChatMessage> savedMessages =
                messageMapper.selectByConversationId(conversationId);
        String databaseName = new JdbcTemplate(chatAgentDataSource)
                .queryForObject("SELECT DATABASE()", String.class);

        assertNotNull(savedConversation);
        assertEquals(conversationId, savedConversation.getConversationId());
        assertEquals("chat_agent", databaseName);
        assertEquals(2, savedMessages.size());
        assertEquals(1L, savedMessages.get(0).getSequenceNo());
        assertEquals("USER", savedMessages.get(0).getRole());
        assertEquals(2L, savedMessages.get(1).getSequenceNo());
        assertEquals("ASSISTANT", savedMessages.get(1).getRole());
    }
}
