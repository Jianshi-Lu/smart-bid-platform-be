-- V23: 统一资信文件状态字段命名
-- 说明：credential_file_batch 表已随 V22 一并废弃（批次概念移除），
-- 本脚本只保留 credential_file 的 convert_status → status 重命名。

ALTER TABLE credential_file
    CHANGE COLUMN convert_status status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED'
        COMMENT '状态：UPLOADED/CONVERTING/CONVERTED/FAILED',
    DROP INDEX idx_convert_status,
    ADD KEY idx_status (status);
