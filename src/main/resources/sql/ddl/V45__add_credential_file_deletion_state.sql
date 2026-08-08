-- V45: 资信文件软删与删除状态机（与 knowledge_file V44 对称）
-- 资信文件由物理删除改为软删除，保留追溯记录；删除流程同样
-- 记录进行中/失败/完成状态，失败时保留失败点与原因，删除接口幂等续删。

ALTER TABLE credential_file
    ADD COLUMN deleted_at DATETIME NULL
        COMMENT '软删除时间（保留追溯记录，非空即已删除）',
    ADD COLUMN deletion_status VARCHAR(20) NULL
        COMMENT '删除状态：NULL=未删除，DELETING=删除中，FAILED=删除失败，DELETED=已删除(终态)',
    ADD COLUMN deletion_failed_stage VARCHAR(32) NULL
        COMMENT '删除失败阶段：DELETE_OBJECT_ORIGINAL/DELETE_OBJECT_CONVERTED/MARK_DELETED/HISTORY/SYNC_CREDENTIALS',
    ADD COLUMN deletion_error_message VARCHAR(1000) NULL
        COMMENT '删除失败原因（内部排查用，不外露）',
    ADD COLUMN deletion_started_at DATETIME NULL
        COMMENT '删除开始时间，用于进程崩溃后超时抢占';

-- 存量已删数据对齐删除终态（当前无存量软删数据，语句与 knowledge_file 对称保留）
UPDATE credential_file
SET deletion_status = 'DELETED'
WHERE deleted_at IS NOT NULL
  AND deletion_status IS NULL;
