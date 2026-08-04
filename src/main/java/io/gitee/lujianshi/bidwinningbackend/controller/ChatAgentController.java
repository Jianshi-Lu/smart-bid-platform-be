package io.gitee.lujianshi.bidwinningbackend.controller;

import io.gitee.lujianshi.bidwinningbackend.dto.ChatRequest;
import io.gitee.lujianshi.bidwinningbackend.dto.ChatResponse;
import io.gitee.lujianshi.bidwinningbackend.dto.common.Result;
import io.gitee.lujianshi.bidwinningbackend.exception.AppErrorCode;
import io.gitee.lujianshi.bidwinningbackend.exception.AppException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Java 平台对 chat-agent Python 服务的最小同步代理入口。 */
@RestController
@RequestMapping("/chat-agent")
public class ChatAgentController {

    private final RestClient restClient;

    public ChatAgentController(
            @Qualifier("chatAgentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** 接收单轮问题，转发到 Python {@code POST /chat} 并包装平台统一响应。 */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request) {
        try {
            ChatResponse response = restClient.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null
                    || response.answer() == null
                    || response.answer().isBlank()) {
                throw pythonCallFailed(
                        "VALIDATE_PYTHON_RESPONSE",
                        "知识问答服务返回了空回答",
                        null);
            }
            return Result.success(response, "知识问答完成");
        } catch (RestClientException e) {
            throw pythonCallFailed(
                    "PYTHON_CALL",
                    "知识问答服务调用失败",
                    e);
        }
    }

    private AppException pythonCallFailed(
            String stage,
            String message,
            Throwable cause) {
        return new AppException(
                AppErrorCode.CHAT_AGENT_PYTHON_CALL_FAILED,
                "chat-agent",
                "CHAT",
                stage,
                message,
                cause);
    }
}
