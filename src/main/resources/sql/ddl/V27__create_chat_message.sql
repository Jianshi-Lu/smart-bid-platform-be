-- V27: 创建 chat-agent 消息表
-- conversation_id 为逻辑外键，关联 chat_conversation.conversation_id。
-- 一次请求产生的用户消息和助手消息共享 request_id；消息顺序由 sequence_no 决定。

USE chat_agent;

CREATE TABLE chat_message (
    message_id          CHAR(36) NOT NULL COMMENT '消息 UUID',
    conversation_id     CHAR(36) NOT NULL COMMENT '逻辑关联 chat_conversation.conversation_id',

    request_id          CHAR(36) NOT NULL COMMENT '一次问答请求的关联 UUID',
    client_message_id   VARCHAR(64) NULL COMMENT '前端生成的幂等 ID，仅 USER 消息写入',

    role                VARCHAR(20) NOT NULL
                        COMMENT '消息角色：USER/ASSISTANT，必要时可扩展 SYSTEM/TOOL',
    content             LONGTEXT NULL COMMENT '消息正文，支持 Markdown；处理中消息可以为空',
    content_type        VARCHAR(20) NOT NULL DEFAULT 'TEXT'
                        COMMENT '正文类型：TEXT/MARKDOWN 等',
    sequence_no         BIGINT UNSIGNED NOT NULL COMMENT '消息在当前会话中的顺序',

    status              VARCHAR(20) NOT NULL
                        COMMENT '消息状态：PROCESSING/COMPLETED/FAILED/CANCELLED',
    error_code          VARCHAR(64) NULL COMMENT '失败编码',
    error_message       VARCHAR(1000) NULL COMMENT '失败原因',
    metadata            JSON NULL COMMENT '引用、文件、token 用量等扩展信息',

    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP COMMENT '消息最后更新时间',

    PRIMARY KEY (message_id),
    UNIQUE KEY uk_chat_message_conversation_sequence (conversation_id, sequence_no),
    UNIQUE KEY uk_chat_message_conversation_client_id (conversation_id, client_message_id),
    KEY idx_chat_message_request_sequence (conversation_id, request_id, sequence_no)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci
  COMMENT='chat-agent 消息表';
