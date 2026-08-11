-- V32: 创建知识文件生命周期追溯表
-- 每个上传的知识文件对应一行；document_uuid 同时作为对象存储目录根
-- （{documentUuid}/original/...、{documentUuid}/converted/content.md）
-- 与 vector-service Chroma chunk 的稳定身份。状态由 Java 编排推进，
-- 转换与向量化分为两个独立状态列，失败留痕并支持按失败点重试。

CREATE TABLE IF NOT EXISTS knowledge_file (
    knowledge_file_id  BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识文件记录ID',
    document_uuid      CHAR(36) NOT NULL COMMENT '文件单元UUID，Java 生成，对象目录与向量身份',
    original_filename  VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type       VARCHAR(100) NULL COMMENT '原始文件 MIME 类型',
    file_size          BIGINT NOT NULL COMMENT '原始文件大小，字节',
    original_sha256    CHAR(64) NULL COMMENT '原始文件 SHA-256',
    converted_sha256   CHAR(64) NULL COMMENT 'Markdown 正文 SHA-256',
    storage_type       VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT '存储介质 LOCAL/MINIO',
    original_file_path VARCHAR(512) NOT NULL COMMENT '原始文件逻辑路径/object key',
    markdown_file_path VARCHAR(512) NULL COMMENT 'Markdown 逻辑路径/object key',

    convert_status     VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' COMMENT '转换状态：UPLOADED/CONVERTING/CONVERTED/FAILED',
    index_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态：PENDING/VECTORIZING/VECTORIZED/FAILED',
    chunk_count        INT NULL COMMENT 'vector-service 实际写入 Chroma 的 chunk 数',
    error_message      VARCHAR(1000) NULL COMMENT '失败原因（转换或向量化阶段）',
    error_stage        VARCHAR(20) NULL COMMENT '失败阶段：UPLOAD/CONVERT/VECTORIZE/UNKNOWN',

    created_at         DATETIME NOT NULL COMMENT '记录创建时间',
    converted_at       DATETIME NULL COMMENT 'Markdown 转换完成时间',
    indexed_at         DATETIME NULL COMMENT '向量化完成时间',
    deleted_at         DATETIME NULL COMMENT '软删除时间（保留追溯记录）',

    PRIMARY KEY (knowledge_file_id),
    UNIQUE KEY uk_document_uuid (document_uuid),
    KEY idx_convert_status (convert_status),
    KEY idx_index_status (index_status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文件生命周期与追溯';
