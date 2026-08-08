-- V44: 知识文件删除状态机
-- 删除不再是"一次尝试"，而是可追溯、可幂等续删的状态机：
-- deletion_status 记录删除进行中/失败/完成，失败时保留失败点与原因；
-- 行在 deleted_at 写入前保持 active，删除接口重试时从头幂等重放。

ALTER TABLE knowledge_file
    ADD COLUMN deletion_status VARCHAR(20) NULL
        COMMENT '删除状态：NULL=未删除，DELETING=删除中，FAILED=删除失败，DELETED=已删除(终态)',
    ADD COLUMN deletion_failed_stage VARCHAR(32) NULL
        COMMENT '删除失败阶段：DELETE_VECTORS/DELETE_OBJECT_ORIGINAL/DELETE_OBJECT_CONVERTED/MARK_DELETED/HISTORY',
    ADD COLUMN deletion_error_message VARCHAR(1000) NULL
        COMMENT '删除失败原因（内部排查用，不外露）',
    ADD COLUMN deletion_started_at DATETIME NULL
        COMMENT '删除开始时间，用于进程崩溃后超时抢占';

-- 存量已软删数据直接对齐删除终态
UPDATE knowledge_file
SET deletion_status = 'DELETED'
WHERE deleted_at IS NOT NULL
  AND deletion_status IS NULL;
