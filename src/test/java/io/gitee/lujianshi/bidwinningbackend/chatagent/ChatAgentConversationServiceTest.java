package io.gitee.lujianshi.bidwinningbackend.chatagent;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatConversation;
import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatMessage;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatConversationMapper;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatMessageMapper;
import io.gitee.lujianshi.bidwinningbackend.config.ChatAgentProperties;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatResponse;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 验证阶段 0 的会话 ID 映射、幂等、序号和归属边界。 */
class ChatAgentConversationServiceTest {

    @Test
    void shouldMapConversationIdToThreadIdAndReplayIdempotentRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("http://127.0.0.1:8082").build();

        ChatConversation conversation = conversation("conversation-1", 1L);
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        PlatformTransactionManager transactionManager = transactionManager();
        ChatAgentProperties properties = properties(1L);
        List<ChatMessage> insertedMessages = new ArrayList<>();

        when(conversationMapper.selectByIdForUpdate("conversation-1"))
                .thenReturn(conversation);
        when(conversationMapper.selectById("conversation-1"))
                .thenReturn(conversation);
        when(conversationMapper.updateById(any(ChatConversation.class)))
                .thenReturn(1);
        when(messageMapper.selectByConversationAndClientMessageId(
                "conversation-1", "client-1"))
                .thenReturn(null)
                .thenAnswer(invocation -> insertedMessages.stream()
                        .filter(message -> "client-1".equals(message.getClientMessageId()))
                        .findFirst()
                        .orElse(null));
        when(messageMapper.selectAssistantByRequestId(anyString(), anyString()))
                .thenAnswer(invocation -> insertedMessages.stream()
                        .filter(message -> "ASSISTANT".equals(message.getRole()))
                        .filter(message -> invocation.getArgument(1, String.class)
                                .equals(message.getRequestId()))
                        .findFirst()
                        .orElse(null));
        when(messageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            insertedMessages.add(invocation.getArgument(0, ChatMessage.class));
            return 1;
        });
        when(messageMapper.selectById(anyString())).thenAnswer(invocation ->
                insertedMessages.stream()
                        .filter(message -> invocation.getArgument(0, String.class)
                                .equals(message.getMessageId()))
                        .findFirst()
                        .orElse(null));
        when(messageMapper.updateById(any(ChatMessage.class))).thenReturn(1);

        ChatAgentConversationService service = new ChatAgentConversationService(
                restClient,
                conversationMapper,
                messageMapper,
                properties,
                transactionManager);

        server.expect(once(), requestTo("http://127.0.0.1:8082/chat"))
                .andExpect(content().json(
                        "{\"question\":\"第一个问题\",\"thread_id\":\"conversation-1\"}"))
                .andRespond(withSuccess(
                        "{\"answer\":\"第一个回答\"}",
                        MediaType.APPLICATION_JSON));

        ChatRequest request = new ChatRequest(
                "conversation-1", "client-1", "第一个问题");
        ChatResponse first = service.chat(request);
        ChatResponse replay = service.chat(request);

        assertEquals(first, replay);
        assertEquals("conversation-1", first.conversationId());
        assertNotNull(first.requestId());
        assertEquals("client-1", first.clientMessageId());
        assertEquals("第一个回答", first.answer());
        assertEquals(2, insertedMessages.size());
        assertEquals(1L, insertedMessages.get(0).getSequenceNo());
        assertEquals(2L, insertedMessages.get(1).getSequenceNo());
        assertEquals(3L, conversation.getNextSequenceNo());
        server.verify();
    }

    @Test
    void shouldRejectConversationOwnedByAnotherUser() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        when(conversationMapper.selectById("conversation-foreign"))
                .thenReturn(conversation("conversation-foreign", 2L));

        ChatAgentConversationService service = new ChatAgentConversationService(
                mock(RestClient.class),
                conversationMapper,
                mock(ChatMessageMapper.class),
                properties(1L),
                transactionManager());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.listMessages("conversation-foreign"));

        assertEquals(
                AppErrorCode.CHAT_AGENT_CONVERSATION_FORBIDDEN,
                exception.getErrorCode());
    }

    private static ChatConversation conversation(String id, long userId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(id);
        conversation.setUserId(userId);
        conversation.setTitle("测试会话");
        conversation.setNextSequenceNo(1L);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private static ChatAgentProperties properties(long userId) {
        ChatAgentProperties properties = new ChatAgentProperties();
        properties.setUserId(userId);
        return properties;
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any()))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        doNothing().when(manager).commit(any());
        doNothing().when(manager).rollback(any());
        return manager;
    }
}
