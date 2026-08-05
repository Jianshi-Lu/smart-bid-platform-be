package io.gitee.lujianshi.bidwinningbackend.dto;

import io.gitee.lujianshi.bidwinningbackend.chatagent.entity.ChatConversation;

import java.time.LocalDateTime;

/** 前端会话列表和切换所需的最小会话视图。 */
public record ConversationResponse(
        String conversationId,
        String title,
        String status,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ConversationResponse from(ChatConversation conversation) {
        return new ConversationResponse(
                conversation.getConversationId(),
                conversation.getTitle(),
                conversation.getStatus(),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
