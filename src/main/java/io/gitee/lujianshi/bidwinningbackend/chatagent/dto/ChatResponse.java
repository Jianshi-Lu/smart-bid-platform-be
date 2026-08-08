package io.gitee.lujianshi.bidwinningbackend.chatagent.dto;

/** Java 平台返回的已持久化问答结果。 */
public record ChatResponse(
        String conversationId,
        String requestId,
        String clientMessageId,
        String answer,
        String title) {
}
