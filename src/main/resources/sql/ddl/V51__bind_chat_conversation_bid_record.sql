-- V51: 会话可选绑定一个不可变的投标记录范围。
-- NULL 表示公共政策问答；非 NULL 时 Java 在每次请求中按该 ID 构造只读 bid_context，
-- Python 在 credential_file_chunks 中按此 ID 过滤资信文件检索。

USE chat_agent;

ALTER TABLE chat_conversation
    ADD COLUMN bid_record_id INT NULL
        COMMENT '创建会话时绑定的投标记录 ID；NULL 表示公共知识会话'
        AFTER user_id;

CREATE INDEX idx_chat_conversation_bid_record
    ON chat_conversation (bid_record_id);
