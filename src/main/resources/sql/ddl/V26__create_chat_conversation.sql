-- V26: 创建 chat-agent 对话表
-- conversation_id 是业务层对外使用的稳定会话 ID，同时作为 LangGraph 的 thread_id。
-- 当前项目尚未接入鉴权，因此 user_id 暂作为会话归属字段保留；不在数据库层建立 FOREIGN KEY。

USE chat_agent;

CREATE TABLE chat_conversation (
    conversation_id  CHAR(36) NOT NULL COMMENT '会话 UUID，同时对应 LangGraph thread_id',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户 ID，当前由应用层负责归属校验',
    title            VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '侧边栏显示标题',
    next_sequence_no BIGINT UNSIGNED NOT NULL DEFAULT 1
                     COMMENT '下一个可分配的消息序号，Java 需锁定会话行后递增分配',

    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                     COMMENT '会话状态：ACTIVE/ARCHIVED/DELETED',
    last_message_at  DATETIME NULL COMMENT '最后一条业务消息时间，用于会话列表排序',

    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                     ON UPDATE CURRENT_TIMESTAMP COMMENT '会话最后更新时间',
    deleted_at       DATETIME NULL COMMENT '软删除时间',

    PRIMARY KEY (conversation_id),
    KEY idx_chat_conversation_user_status_last_message
        (user_id, status, last_message_at, conversation_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci
  COMMENT='chat-agent 对话表';
