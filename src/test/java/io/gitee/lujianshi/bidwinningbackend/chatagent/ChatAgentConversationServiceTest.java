package io.gitee.lujianshi.bidwinningbackend.chatagent;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatConversation;
import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatMessage;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatConversationMapper;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatMessageMapper;
import io.gitee.lujianshi.bidwinningbackend.config.ChatAgentProperties;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.ChatRequest;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.ChatResponse;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.ConversationResponse;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.CreateConversationRequest;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                        "{\"question\":\"第一个问题\",\"thread_id\":\"conversation-1\"}",
                        true))
                .andRespond(withSuccess(
                        "{\"answer\":\"第一个回答\","
                                + "\"referenced_bid_record_ids\":[17,17,-1]}",
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
        assertEquals(List.of(17), first.referencedBidRecordIds());
        assertEquals(List.of(17), replay.referencedBidRecordIds());
        assertEquals("第一个回答", replay.answer());
        assertEquals(
                "第一个回答\n\n<!-- bid-record-refs: 17 -->",
                insertedMessages.get(1).getContent());
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

    @Test
    void shouldCreateConversationWithoutBidRecordScope() {
        ChatConversationMapper conversationMapper =
                mock(ChatConversationMapper.class);
        ChatConversation[] inserted = new ChatConversation[1];
        when(conversationMapper.insert(any(ChatConversation.class)))
                .thenAnswer(invocation -> {
                    inserted[0] = invocation.getArgument(
                            0, ChatConversation.class);
                    return 1;
                });
        when(conversationMapper.selectById(anyString()))
                .thenAnswer(invocation -> inserted[0]);
        ChatAgentConversationService service = new ChatAgentConversationService(
                mock(RestClient.class),
                conversationMapper,
                mock(ChatMessageMapper.class),
                properties(1L),
                transactionManager());
        ConversationResponse response = service.createConversation(
                new CreateConversationRequest("投标问答"));

        assertEquals("投标问答", response.title());
        assertEquals("投标问答", inserted[0].getTitle());
    }

    @Test
    void shouldPersistAutoTitleFromPythonResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("http://127.0.0.1:8082").build();

        ChatConversation conversation = conversation("conversation-title", 1L);
        conversation.setTitle("新对话");
        conversation.setTitleSource("AUTO");
        Harness harness = harness(server, restClient, conversation);

        server.expect(once(), requestTo("http://127.0.0.1:8082/chat"))
                .andRespond(withSuccess(
                        "{\"answer\":\"回答\",\"title\":\"自动标题\"}",
                        MediaType.APPLICATION_JSON));
        when(harness.conversationMapper()
                .updateTitleIfAuto("conversation-title", "自动标题"))
                .thenReturn(1);

        ChatResponse response = harness.service().chat(
                new ChatRequest(
                        "conversation-title", "client-title", "问题"));

        assertEquals("自动标题", response.title());
        verify(harness.conversationMapper())
                .updateTitleIfAuto("conversation-title", "自动标题");
        server.verify();
    }

    @Test
    void shouldNotReturnAutoTitleWhenConversationAlreadyManual() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("http://127.0.0.1:8082").build();

        ChatConversation conversation = conversation("conversation-manual", 1L);
        conversation.setTitle("手动标题");
        conversation.setTitleSource("MANUAL");
        Harness harness = harness(server, restClient, conversation);

        server.expect(once(), requestTo("http://127.0.0.1:8082/chat"))
                .andRespond(withSuccess(
                        "{\"answer\":\"回答\",\"title\":\"自动标题\"}",
                        MediaType.APPLICATION_JSON));
        when(harness.conversationMapper()
                .updateTitleIfAuto("conversation-manual", "自动标题"))
                .thenReturn(0);

        ChatResponse response = harness.service().chat(
                new ChatRequest(
                        "conversation-manual", "client-manual", "问题"));

        assertNull(response.title());
        server.verify();
    }

    @Test
    void shouldUpdateTitleManuallyAndMarkManual() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatConversation conversation = conversation("conversation-rename", 1L);
        conversation.setTitle("新标题");
        conversation.setTitleSource("MANUAL");
        when(conversationMapper.selectById("conversation-rename"))
                .thenReturn(conversation);
        when(conversationMapper.updateTitleManual(
                "conversation-rename", "新标题"))
                .thenReturn(1);

        ChatAgentConversationService service = new ChatAgentConversationService(
                mock(RestClient.class),
                conversationMapper,
                mock(ChatMessageMapper.class),
                properties(1L),
                transactionManager());

        ConversationResponse response =
                service.updateTitle("conversation-rename", "新标题");

        assertEquals("新标题", response.title());
        assertEquals("MANUAL", response.titleSource());
    }

    @Test
    void shouldSoftDeleteOwnedConversation() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatConversation conversation = conversation("conversation-delete", 1L);
        when(conversationMapper.selectById("conversation-delete"))
                .thenReturn(conversation);
        when(conversationMapper.softDeleteById(
                eq("conversation-delete"), any(LocalDateTime.class)))
                .thenReturn(1);

        ChatAgentConversationService service = new ChatAgentConversationService(
                mock(RestClient.class),
                conversationMapper,
                mock(ChatMessageMapper.class),
                properties(1L),
                transactionManager());

        ConversationResponse response =
                service.deleteConversation("conversation-delete");

        assertEquals("DELETED", response.status());
        verify(conversationMapper).softDeleteById(
                eq("conversation-delete"), any(LocalDateTime.class));
    }

    @Test
    void shouldRejectDeletingForeignConversation() {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        when(conversationMapper.selectById("conversation-foreign-delete"))
                .thenReturn(conversation("conversation-foreign-delete", 2L));

        ChatAgentConversationService service = new ChatAgentConversationService(
                mock(RestClient.class),
                conversationMapper,
                mock(ChatMessageMapper.class),
                properties(1L),
                transactionManager());

        AppException exception = assertThrows(
                AppException.class,
                () -> service.deleteConversation("conversation-foreign-delete"));

        assertEquals(
                AppErrorCode.CHAT_AGENT_CONVERSATION_FORBIDDEN,
                exception.getErrorCode());
    }

    private Harness harness(
            MockRestServiceServer server,
            RestClient restClient,
            ChatConversation conversation) {
        ChatConversationMapper conversationMapper = mock(ChatConversationMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        List<ChatMessage> insertedMessages = new ArrayList<>();

        when(conversationMapper.selectByIdForUpdate(conversation.getConversationId()))
                .thenReturn(conversation);
        when(conversationMapper.selectById(conversation.getConversationId()))
                .thenReturn(conversation);
        when(conversationMapper.updateById(any(ChatConversation.class)))
                .thenReturn(1);
        when(messageMapper.selectByConversationAndClientMessageId(
                eq(conversation.getConversationId()), anyString()))
                .thenAnswer(invocation -> insertedMessages.stream()
                        .filter(message -> invocation.getArgument(1, String.class)
                                .equals(message.getClientMessageId()))
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
                properties(1L),
                transactionManager());
        return new Harness(service, conversationMapper, messageMapper);
    }

    private static ChatConversation conversation(String id, long userId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(id);
        conversation.setUserId(userId);
        conversation.setTitle("测试会话");
        conversation.setTitleSource("AUTO");
        conversation.setNextSequenceNo(1L);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private record Harness(
            ChatAgentConversationService service,
            ChatConversationMapper conversationMapper,
            ChatMessageMapper messageMapper) {
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
