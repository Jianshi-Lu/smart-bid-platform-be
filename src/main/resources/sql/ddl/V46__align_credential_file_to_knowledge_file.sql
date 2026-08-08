-- V46: credential_file 表设计对齐 knowledge_file
-- 字段命名与知识文件一致（sha256 → original_sha256、status → convert_status），
-- 补充 converted_sha256 与 error_stage，并给 file_uuid 加唯一约束；
-- bid_record_id / file_order / bound_at 为资信业务专属字段，保留不动。

ALTER TABLE credential_file
    RENAME COLUMN sha256 TO original_sha256,
    RENAME COLUMN status TO convert_status,
    ADD COLUMN converted_sha256 CHAR(64) NULL
        COMMENT '转换后 Markdown 正文 SHA-256',
    ADD COLUMN error_stage VARCHAR(20) NULL
        COMMENT '失败阶段：UPLOAD/CONVERT/UNKNOWN',
    ADD UNIQUE KEY uk_file_uuid (file_uuid);
