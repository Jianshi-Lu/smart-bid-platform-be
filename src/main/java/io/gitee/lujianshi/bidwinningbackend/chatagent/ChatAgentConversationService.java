package io.gitee.lujianshi.bidwinningbackend.chatagent;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatConversation;
import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatMessage;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatConversationMapper;
import io.gitee.lujianshi.bidwinningbackend.chatagent.mapper.ChatMessageMapper;
import io.gitee.lujianshi.bidwinningbackend.config.ChatAgentProperties;
import io.gitee.lujianshi.bidwinningbackend.config.CurrentAccountContext;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.ChatMessageResponse;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.ChatRequest;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.ChatResponse;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.ConversationResponse;
import io.gitee.lujianshi.bidwinningbackend.chatagent.dto.CreateConversationRequest;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * chat-agent 阶段 0 的会话协调边界。
 *
 * <p>Java 在这里负责业务会话、用户归属、幂等消息和顺序号；Python 只接收
 * 已经明确映射的 {@code thread_id}。同一 JVM 内同一会话使用独立锁，不同会话
 * 不共享锁，因此可以并行执行。</p>
 */
@Service
public class ChatAgentConversationService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";
    private static final String AUTO = "AUTO";
    private static final String MANUAL = "MANUAL";
    private static final String USER = "USER";
    private static final String ASSISTANT = "ASSISTANT";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";
    private static final String TEXT = "TEXT";
    private static final Pattern BID_RECORD_REFERENCE_MARKER = Pattern.compile(
            "<!--\\s*bid-record-refs\\s*:\\s*([0-9,\\s]+)\\s*-->");
    private static final int MAX_REFERENCED_BID_RECORDS = 12;

    private static final Logger log = LoggerFactory.getLogger(
            ChatAgentConversationService.class);

    private final RestClient restClient;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatAgentProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 锁按 conversationId 分桶。阶段 0 是单 Java 实例部署；多实例时仍需升级为
     * Redis/数据库分布式锁，数据库行锁只保护短事务内的序号分配。
     */
    private final ConcurrentMap<String, ReentrantLock> conversationLocks =
            new ConcurrentHashMap<>();

    public ChatAgentConversationService(
            @Qualifier("chatAgentRestClient") RestClient restClient,
            ChatConversationMapper conversationMapper,
            ChatMessageMapper messageMapper,
            ChatAgentProperties properties,
            @Qualifier("chatAgentTransactionManager")
            PlatformTransactionManager transactionManager) {
        this.restClient = restClient;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 创建服务端生成 ID 的新会话。 */
    public ConversationResponse createConversation(CreateConversationRequest request) {
        String title = request == null || request.title() == null
                || request.title().isBlank()
                ? "新对话"
                : request.title().trim();
        ChatConversation conversation = transactionTemplate.execute(status -> {
            ChatConversation created = new ChatConversation();
            created.setConversationId(UUID.randomUUID().toString());
            created.setUserId(currentUserId());
            created.setTitle(title);
            created.setTitleSource(AUTO);
            created.setNextSequenceNo(1L);
            created.setStatus(ACTIVE);
            conversationMapper.insert(created);
            return conversationMapper.selectById(created.getConversationId());
        });
        return ConversationResponse.from(required(conversation, "创建会话后读取失败"));
    }

    /** 返回当前单用户边界下的会话列表。 */
    public List<ConversationResponse> listConversations() {
        return conversationMapper.selectByUserId(currentUserId()).stream()
                .map(ConversationResponse::from)
                .toList();
    }

    /** 返回已校验归属的会话消息。 */
    public List<ChatMessageResponse> listMessages(String conversationId) {
        requireOwnedConversation(conversationId);
        return messageMapper.selectByConversationId(conversationId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /** 执行一次带持久化和幂等保护的同步问答。 */
    public ChatResponse chat(ChatRequest request) {
        String conversationId = request.conversationId().trim();
        ReentrantLock lock = conversationLocks.computeIfAbsent(
                conversationId, ignored -> new ReentrantLock());

        lock.lock();
        try {
            PreparedRequest prepared = prepare(request, conversationId);
            if (prepared.completed()) {
                return responseOf(prepared);
            }

            try {
                PythonChatResponse pythonResponse = callPython(
                        prepared.question(),
                        conversationId);
                if (pythonResponse == null
                        || pythonResponse.answer() == null
                        || pythonResponse.answer().isBlank()) {
                    throw pythonCallFailed(
                            "VALIDATE_PYTHON_RESPONSE",
                            "知识问答服务返回了空回答",
                            null);
                }
                String persistedTitle = complete(prepared, pythonResponse);
                return responseOf(
                        prepared,
                        pythonResponse.answer(),
                        persistedTitle,
                        normalizeReferencedBidRecordIds(
                                pythonResponse.referencedBidRecordIds()));
            } catch (RestClientException e) {
                fail(prepared, "PYTHON_CALL", "知识问答服务调用失败");
                throw pythonCallFailed(
                        "PYTHON_CALL",
                        "知识问答服务调用失败",
                        e);
            } catch (AppException e) {
                fail(prepared, e.getStage(), e.getPublicMessage());
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    private PreparedRequest prepare(ChatRequest request, String conversationId) {
        String clientMessageId = request.clientMessageId().trim();
        String question = request.question().trim();

        return transactionTemplate.execute(status -> {
            ChatConversation conversation = conversationMapper
                    .selectByIdForUpdate(conversationId);
            requireOwnedConversation(conversation);

            ChatMessage existingUser = messageMapper
                    .selectByConversationAndClientMessageId(
                            conversationId, clientMessageId);
            if (existingUser != null) {
                if (!question.equals(existingUser.getContent())) {
                    throw new AppException(
                            AppErrorCode.CHAT_AGENT_IDEMPOTENCY_CONFLICT,
                            "chat-agent",
                            "CHAT",
                            "IDEMPOTENCY",
                            "同一个 clientMessageId 不能提交不同的问题");
                }

                ChatMessage existingAssistant = messageMapper
                        .selectAssistantByRequestId(
                                conversationId, existingUser.getRequestId());
                if (existingAssistant == null) {
                    throw new AppException(
                            AppErrorCode.DATABASE_CONSTRAINT_VIOLATION,
                            "chat-agent",
                            "CHAT",
                            "LOAD_MESSAGE",
                            "用户消息缺少对应的助手消息");
                }
                if (COMPLETED.equals(existingAssistant.getStatus())) {
                    return new PreparedRequest(
                            conversationId,
                            existingUser.getRequestId(),
                            clientMessageId,
                            question,
                            existingAssistant,
                            true);
                }

                existingAssistant.setStatus(PROCESSING);
                existingAssistant.setContent(null);
                existingAssistant.setErrorCode(null);
                existingAssistant.setErrorMessage(null);
                messageMapper.updateById(existingAssistant);
                return new PreparedRequest(
                        conversationId,
                        existingUser.getRequestId(),
                        clientMessageId,
                        question,
                        existingAssistant,
                        false);
            }

            String requestId = UUID.randomUUID().toString();
            long userSequence = nextSequence(conversation);
            long assistantSequence = userSequence + 1;

            ChatMessage userMessage = new ChatMessage();
            userMessage.setMessageId(UUID.randomUUID().toString());
            userMessage.setConversationId(conversationId);
            userMessage.setRequestId(requestId);
            userMessage.setClientMessageId(clientMessageId);
            userMessage.setRole(USER);
            userMessage.setContent(question);
            userMessage.setContentType(TEXT);
            userMessage.setSequenceNo(userSequence);
            userMessage.setStatus(COMPLETED);
            messageMapper.insert(userMessage);

            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setMessageId(UUID.randomUUID().toString());
            assistantMessage.setConversationId(conversationId);
            assistantMessage.setRequestId(requestId);
            assistantMessage.setRole(ASSISTANT);
            assistantMessage.setContentType(TEXT);
            assistantMessage.setSequenceNo(assistantSequence);
            assistantMessage.setStatus(PROCESSING);
            messageMapper.insert(assistantMessage);

            conversation.setNextSequenceNo(assistantSequence + 1);
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
            return new PreparedRequest(
                    conversationId,
                    requestId,
                    clientMessageId,
                    question,
                    assistantMessage,
                    false);
        });
    }

    private PythonChatResponse callPython(
            String question,
            String threadId) {
        return restClient.post()
                .uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new PythonChatRequest(question, threadId))
                .retrieve()
                .body(PythonChatResponse.class);
    }

    private String complete(
            PreparedRequest prepared,
            PythonChatResponse pythonResponse) {
        String persistedAnswer = withReferenceMarker(
                pythonResponse.answer(),
                pythonResponse.referencedBidRecordIds());
        transactionTemplate.executeWithoutResult(status -> {
            ChatMessage assistant = messageMapper.selectById(
                    prepared.assistantMessage().getMessageId());
            if (assistant == null) {
                throw new AppException(
                        AppErrorCode.DATABASE_CONSTRAINT_VIOLATION,
                        "chat-agent",
                        "CHAT",
                        "COMPLETE_MESSAGE",
                        "助手消息不存在");
            }
            assistant.setContent(persistedAnswer);
            assistant.setStatus(COMPLETED);
            assistant.setErrorCode(null);
            assistant.setErrorMessage(null);
            messageMapper.updateById(assistant);

            ChatConversation conversation = conversationMapper
                    .selectByIdForUpdate(prepared.conversationId());
            requireOwnedConversation(conversation);
            conversation.setLastMessageAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        });

        String pythonTitle = pythonResponse.title();
        if (pythonTitle == null || pythonTitle.isBlank()) {
            return null;
        }

        String normalizedTitle = pythonTitle.trim();
        try {
            int updated = conversationMapper.updateTitleIfAuto(
                    prepared.conversationId(),
                    normalizedTitle);
            return updated > 0 ? normalizedTitle : null;
        } catch (RuntimeException e) {
            // 标题持久化失败不能影响已完成的问答；降级为不返回标题。
            log.warn(
                    "自动标题写入失败，conversationId={}",
                    prepared.conversationId(),
                    e);
            return null;
        }
    }

    /** 手动修改会话标题，并标记为 MANUAL，后续自动生成不再覆盖。 */
    public ConversationResponse updateTitle(
            String conversationId,
            String title) {
        requireOwnedConversation(conversationId);
        String normalizedTitle = title.trim();
        conversationMapper.updateTitleManual(conversationId, normalizedTitle);
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        return ConversationResponse.from(required(conversation, "更新标题后读取失败"));
    }

    /** 软删除会话；已删除时幂等返回删除后的会话视图。 */
    public ConversationResponse deleteConversation(String conversationId) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new AppException(
                    AppErrorCode.CHAT_AGENT_CONVERSATION_NOT_FOUND,
                    "chat-agent",
                    "CONVERSATION",
                    "DELETE_CONVERSATION",
                    "会话不存在");
        }
        if (!currentUserId().equals(conversation.getUserId())) {
            throw new AppException(
                    AppErrorCode.CHAT_AGENT_CONVERSATION_FORBIDDEN,
                    "chat-agent",
                    "CONVERSATION",
                    "DELETE_CONVERSATION",
                    "当前用户无权删除该会话");
        }

        LocalDateTime deletedAt = LocalDateTime.now();
        conversationMapper.softDeleteById(conversationId, deletedAt);
        conversation.setStatus(DELETED);
        conversation.setDeletedAt(deletedAt);
        return ConversationResponse.from(conversation);
    }

    private void fail(PreparedRequest prepared, String errorCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            ChatMessage assistant = messageMapper.selectById(
                    prepared.assistantMessage().getMessageId());
            if (assistant == null) {
                return;
            }
            assistant.setStatus(FAILED);
            assistant.setErrorCode(errorCode);
            assistant.setErrorMessage(message);
            messageMapper.updateById(assistant);
        });
    }

    private void requireOwnedConversation(String conversationId) {
        requireOwnedConversation(conversationMapper.selectById(conversationId));
    }

    private void requireOwnedConversation(ChatConversation conversation) {
        if (conversation == null) {
            throw new AppException(
                    AppErrorCode.CHAT_AGENT_CONVERSATION_NOT_FOUND,
                    "chat-agent",
                    "CONVERSATION",
                    "LOAD_CONVERSATION",
                    "会话不存在");
        }
        if (!currentUserId().equals(conversation.getUserId())) {
            throw new AppException(
                    AppErrorCode.CHAT_AGENT_CONVERSATION_FORBIDDEN,
                    "chat-agent",
                    "CONVERSATION",
                    "CHECK_OWNERSHIP",
                    "当前用户无权访问该会话");
        }
        if (!ACTIVE.equals(conversation.getStatus())) {
            throw new AppException(
                    AppErrorCode.COMMON_INVALID_ARGUMENT,
                    "chat-agent",
                    "CONVERSATION",
                    "CHECK_STATUS",
                    "当前会话不可继续提问");
        }
    }

    private long nextSequence(ChatConversation conversation) {
        return conversation.getNextSequenceNo() == null
                ? 1L
                : conversation.getNextSequenceNo();
    }

    private Long currentUserId() {
        CurrentAccountContext.CurrentAccount account =
                CurrentAccountContext.get();
        if (account == null
                || !CurrentAccountContext.BID_INVITER.equals(
                        account.operatorType())
                || account.operatorId() == null) {
            throw new AppException(
                    AppErrorCode.BID_INVITER_ACCOUNT_UNAUTHORIZED,
                    "chat-agent",
                    "CONVERSATION",
                    "RESOLVE_CURRENT_ACCOUNT",
                    "请先登录");
        }
        return account.operatorId();
    }

    private ChatResponse responseOf(PreparedRequest prepared) {
        String persistedAnswer = prepared.assistantMessage().getContent();
        return responseOf(
                prepared,
                withoutReferenceMarker(persistedAnswer),
                null,
                referencedBidRecordIds(persistedAnswer));
    }

    private ChatResponse responseOf(
            PreparedRequest prepared,
            String answer,
            String title,
            List<Integer> referencedBidRecordIds) {
        return new ChatResponse(
                prepared.conversationId(),
                prepared.requestId(),
                prepared.clientMessageId(),
                answer,
                title,
                referencedBidRecordIds);
    }

    /**
     * 把 Python 从实际 ToolMessage 中提取的资料范围同回答一起持久化。
     * 标记是 Markdown 注释，不改变用户看到的正文；前端历史回放时可重新生成下载入口。
     */
    private String withReferenceMarker(
            String answer,
            List<Integer> referencedBidRecordIds) {
        List<Integer> normalized = normalizeReferencedBidRecordIds(
                referencedBidRecordIds);
        if (normalized.isEmpty()) {
            return answer;
        }
        StringBuilder ids = new StringBuilder();
        for (int index = 0; index < normalized.size(); index++) {
            if (index > 0) {
                ids.append(',');
            }
            ids.append(normalized.get(index));
        }
        return answer + "\n\n<!-- bid-record-refs: " + ids + " -->";
    }

    private String withoutReferenceMarker(String answer) {
        return answer == null
                ? null
                : BID_RECORD_REFERENCE_MARKER.matcher(answer).replaceAll("").strip();
    }

    private List<Integer> referencedBidRecordIds(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        Matcher marker = BID_RECORD_REFERENCE_MARKER.matcher(answer);
        if (!marker.find()) {
            return List.of();
        }
        String[] rawIds = marker.group(1).split(",");
        java.util.ArrayList<Integer> parsed = new java.util.ArrayList<>();
        for (String rawId : rawIds) {
            try {
                parsed.add(Integer.parseInt(rawId.trim()));
            } catch (NumberFormatException ignored) {
                // 历史消息中的损坏注释只会失去下载入口，不能导致整条会话回放失败。
            }
        }
        return normalizeReferencedBidRecordIds(parsed);
    }

    private List<Integer> normalizeReferencedBidRecordIds(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<Integer> normalized = new java.util.LinkedHashSet<>();
        for (Integer value : values) {
            if (value != null && value > 0) {
                normalized.add(value);
            }
            if (normalized.size() >= MAX_REFERENCED_BID_RECORDS) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    private AppException pythonCallFailed(
            String stage, String message, Throwable cause) {
        return new AppException(
                AppErrorCode.CHAT_AGENT_PYTHON_CALL_FAILED,
                "chat-agent",
                "CHAT",
                stage,
                message,
                cause);
    }

    private static <T> T required(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private record PreparedRequest(
            String conversationId,
            String requestId,
            String clientMessageId,
            String question,
            ChatMessage assistantMessage,
            boolean completed) {
    }

    private record PythonChatRequest(
            String question,
            @JsonProperty("thread_id") String threadId) {
    }

    private record PythonChatResponse(
            String answer,
            String title,
            @JsonProperty("referenced_bid_record_ids")
            List<Integer> referencedBidRecordIds) {
    }
}
