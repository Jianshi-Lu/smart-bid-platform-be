package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.chatagent.ChatAgentConversationService;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatMessageResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.ConversationResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.CreateConversationRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Java 平台对 chat-agent 的会话、历史和问答入口。 */
@RestController
@RequestMapping("/chat-agent")
public class ChatAgentController {

    private final ChatAgentConversationService conversationService;

    public ChatAgentController(ChatAgentConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /** 创建服务端生成 ID 的会话。 */
    @PostMapping(value = "/conversations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ConversationResponse> createConversation(
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        return Result.success(
                conversationService.createConversation(request),
                "会话创建成功");
    }

    /** 查询当前单用户边界下的会话列表。 */
    @GetMapping("/conversations")
    public Result<List<ConversationResponse>> listConversations() {
        return Result.success(
                conversationService.listConversations(),
                "会话查询成功");
    }

    /** 查询一个已校验归属的会话消息。 */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<ChatMessageResponse>> listMessages(
            @PathVariable String conversationId) {
        return Result.success(
                conversationService.listMessages(conversationId),
                "消息查询成功");
    }

    /** 执行一次带 conversationId/thread_id 映射和幂等保护的问答。 */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return Result.success(
                conversationService.chat(request),
                "知识问答完成");
    }
}
