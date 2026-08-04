package io.gitee.lujianshi.bidwinningbackend.chatagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** chat-agent 消息实体，对应 chat_agent.chat_message。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.INPUT)
    private String messageId;

    private String conversationId;

    private String requestId;

    private String clientMessageId;

    private String role;

    private String content;

    private String contentType;

    private Long sequenceNo;

    private String status;

    private String errorCode;

    private String errorMessage;

    /** 初始阶段保留 JSON 原文，避免为持久层验证额外引入 TypeHandler。 */
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
