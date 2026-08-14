-- V50: 为资信文件增加独立向量化状态。
-- 资信向量写入 vector-service 的 credential_file_chunks 私有 collection，
-- 与公共知识 knowledge_file_chunks 完全隔离。

ALTER TABLE credential_file
    ADD COLUMN index_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT '向量化状态：PENDING/VECTORIZING/VECTORIZED/FAILED'
        AFTER convert_status,
    ADD COLUMN chunk_count INT NULL
        COMMENT 'vector-service 实际写入的资信 chunk 数'
        AFTER index_status,
    ADD COLUMN indexed_at DATETIME NULL
        COMMENT '最近一次向量化完成时间'
        AFTER converted_at;

CREATE INDEX idx_credential_file_index_status
    ON credential_file (index_status);
