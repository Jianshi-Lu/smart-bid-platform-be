-- V53: 为结构化投标记录增加独立向量化状态。
-- 投标记录写入私有 bid_record_chunks collection，
-- 与公共知识 knowledge_file_chunks 完全隔离。
-- 每个字段独立守卫，允许手工执行中断后从部分迁移状态继续。

USE `bid-winning`;

SET @add_index_status_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'index_status'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN index_status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''结构化投标记录向量状态：PENDING/VECTORIZING/VECTORIZED/FAILED'' AFTER credentials'
);
PREPARE add_index_status_stmt FROM @add_index_status_sql;
EXECUTE add_index_status_stmt;
DEALLOCATE PREPARE add_index_status_stmt;

SET @add_chunk_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'chunk_count'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN chunk_count INT NULL COMMENT ''vector-service 实际写入的结构化投标记录文档数，正常为 1'' AFTER index_status'
);
PREPARE add_chunk_count_stmt FROM @add_chunk_count_sql;
EXECUTE add_chunk_count_stmt;
DEALLOCATE PREPARE add_chunk_count_stmt;

SET @add_indexed_at_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'indexed_at'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN indexed_at DATETIME NULL COMMENT ''最近一次结构化投标记录向量化完成时间'' AFTER chunk_count'
);
PREPARE add_indexed_at_stmt FROM @add_indexed_at_sql;
EXECUTE add_indexed_at_stmt;
DEALLOCATE PREPARE add_indexed_at_stmt;

SET @add_index_error_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'index_error'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN index_error VARCHAR(1000) NULL COMMENT ''最近一次结构化投标记录向量化失败原因'' AFTER indexed_at'
);
PREPARE add_index_error_stmt FROM @add_index_error_sql;
EXECUTE add_index_error_stmt;
DEALLOCATE PREPARE add_index_error_stmt;

SET @add_index_status_index_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND INDEX_NAME = 'idx_bid_record_index_status'),
    'SELECT 1',
    'CREATE INDEX idx_bid_record_index_status ON bid_record (index_status)'
);
PREPARE add_index_status_index_stmt FROM @add_index_status_index_sql;
EXECUTE add_index_status_index_stmt;
DEALLOCATE PREPARE add_index_status_index_stmt;
