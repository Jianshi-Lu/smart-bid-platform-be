package io.gitee.lujianshi.bidwinningbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 提交给 chat-agent 的带会话上下文问答请求。 */
public record ChatRequest(
        @NotBlank(message = "会话 ID 不能为空")
        @Size(max = 36, message = "会话 ID 长度不能超过 36")
        String conversationId,
        @NotBlank(message = "客户端消息 ID 不能为空")
        @Size(max = 64, message = "客户端消息 ID 长度不能超过 64")
        String clientMessageId,
        @NotBlank(message = "问题不能为空")
        String question) {
}
