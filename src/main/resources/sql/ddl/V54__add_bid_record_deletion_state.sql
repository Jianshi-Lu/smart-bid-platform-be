-- V54: 为投标记录增加跨数据库、MinIO 与 Chroma 的可续作删除状态。
-- 删除成功后物理移除 bid_record；这些字段只在删除进行中或失败时保留恢复锚点。

USE `bid-winning`;

SET @add_deletion_status_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'deletion_status'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN deletion_status VARCHAR(20) NULL COMMENT ''跨资源删除状态：NULL/DELETING/FAILED'' AFTER index_error'
);
PREPARE add_deletion_status_stmt FROM @add_deletion_status_sql;
EXECUTE add_deletion_status_stmt;
DEALLOCATE PREPARE add_deletion_status_stmt;

SET @add_deletion_started_at_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'deletion_started_at'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN deletion_started_at DATETIME NULL COMMENT ''最近一次删除尝试开始时间'' AFTER deletion_status'
);
PREPARE add_deletion_started_at_stmt FROM @add_deletion_started_at_sql;
EXECUTE add_deletion_started_at_stmt;
DEALLOCATE PREPARE add_deletion_started_at_stmt;

SET @add_deletion_failed_stage_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'deletion_failed_stage'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN deletion_failed_stage VARCHAR(100) NULL COMMENT ''最近一次删除失败阶段'' AFTER deletion_started_at'
);
PREPARE add_deletion_failed_stage_stmt FROM @add_deletion_failed_stage_sql;
EXECUTE add_deletion_failed_stage_stmt;
DEALLOCATE PREPARE add_deletion_failed_stage_stmt;

SET @add_deletion_error_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND COLUMN_NAME = 'deletion_error'),
    'SELECT 1',
    'ALTER TABLE bid_record ADD COLUMN deletion_error VARCHAR(1000) NULL COMMENT ''最近一次删除失败原因'' AFTER deletion_failed_stage'
);
PREPARE add_deletion_error_stmt FROM @add_deletion_error_sql;
EXECUTE add_deletion_error_stmt;
DEALLOCATE PREPARE add_deletion_error_stmt;

SET @add_deletion_status_index_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bid_record'
              AND INDEX_NAME = 'idx_bid_record_deletion_status'),
    'SELECT 1',
    'CREATE INDEX idx_bid_record_deletion_status ON bid_record (deletion_status)'
);
PREPARE add_deletion_status_index_stmt FROM @add_deletion_status_index_sql;
EXECUTE add_deletion_status_index_stmt;
DEALLOCATE PREPARE add_deletion_status_index_stmt;
