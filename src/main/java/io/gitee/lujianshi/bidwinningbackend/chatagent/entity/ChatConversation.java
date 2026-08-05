package io.gitee.lujianshi.bidwinningbackend.chatagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** chat-agent 对话实体，对应 chat_agent.chat_conversation。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.INPUT)
    private String conversationId;

    private Long userId;

    private String title;

    private String titleSource;

    private Long nextSequenceNo;

    private String status;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
