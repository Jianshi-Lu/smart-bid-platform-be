-- V49: 对齐 credential_file 与 knowledge_file 中不存在业务歧义的公共字段。
-- 业务专属字段（bid_record_id/file_order/bound_at、index_status/chunk_count/indexed_at）保留。

ALTER TABLE credential_file
    MODIFY original_file_path VARCHAR(512) NOT NULL
        COMMENT '原始文件逻辑路径/object key',
    MODIFY markdown_file_path VARCHAR(512) NULL
        COMMENT 'Markdown 逻辑路径/object key',
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '记录创建时间',
    MODIFY error_stage VARCHAR(20) NULL
        COMMENT '失败阶段：UPLOAD/CONVERT/VECTORIZE/UNKNOWN';

ALTER TABLE knowledge_file
    MODIFY original_file_path VARCHAR(512) NOT NULL
        COMMENT '原始文件逻辑路径/object key',
    MODIFY markdown_file_path VARCHAR(512) NULL
        COMMENT 'Markdown 逻辑路径/object key',
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '记录创建时间',
    MODIFY error_stage VARCHAR(20) NULL
        COMMENT '失败阶段：UPLOAD/CONVERT/VECTORIZE/UNKNOWN';
