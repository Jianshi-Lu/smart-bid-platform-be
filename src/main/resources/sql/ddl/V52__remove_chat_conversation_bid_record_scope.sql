-- V52: 撤销 V51 的会话预绑定设计。
-- 投标记录和资信材料统一进入独立的 bid_material_chunks collection，
-- 招标方可在任意会话中通过动态工具检索，不再把投标范围固化到会话。
-- information_schema 守卫让手工 DDL 可在已执行或部分执行后安全重放。

USE chat_agent;

SET @drop_scope_index_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chat_conversation'
          AND INDEX_NAME = 'idx_chat_conversation_bid_record'
    ),
    'ALTER TABLE chat_conversation DROP INDEX idx_chat_conversation_bid_record',
    'SELECT 1'
);
PREPARE drop_scope_index_stmt FROM @drop_scope_index_sql;
EXECUTE drop_scope_index_stmt;
DEALLOCATE PREPARE drop_scope_index_stmt;

SET @drop_scope_column_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chat_conversation'
          AND COLUMN_NAME = 'bid_record_id'
    ),
    'ALTER TABLE chat_conversation DROP COLUMN bid_record_id',
    'SELECT 1'
);
PREPARE drop_scope_column_stmt FROM @drop_scope_column_sql;
EXECUTE drop_scope_column_stmt;
DEALLOCATE PREPARE drop_scope_column_stmt;
