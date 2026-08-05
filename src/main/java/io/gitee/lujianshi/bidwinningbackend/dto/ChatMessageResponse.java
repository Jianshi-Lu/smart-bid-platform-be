package io.gitee.lujianshi.bidwinningbackend.dto;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatMessage;

import java.time.LocalDateTime;

/** 前端回放会话历史所需的消息视图。 */
public record ChatMessageResponse(
        String messageId,
        String conversationId,
        String requestId,
        String clientMessageId,
        String role,
        String content,
        String contentType,
        Long sequenceNo,
        String status,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getMessageId(),
                message.getConversationId(),
                message.getRequestId(),
                message.getClientMessageId(),
                message.getRole(),
                message.getContent(),
                message.getContentType(),
                message.getSequenceNo(),
                message.getStatus(),
                message.getErrorCode(),
                message.getErrorMessage(),
                message.getCreatedAt(),
                message.getUpdatedAt());
    }
}
