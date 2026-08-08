-- V47: credential_file.original_sha256 允许为空
-- 对齐 knowledge_file 的先落库再存储流程：插入 UPLOADED 记录时尚未计算/回填
-- SHA-256，存储并回读校验成功后由 updateOriginalMetadata 回填。

ALTER TABLE credential_file
    MODIFY original_sha256 CHAR(64) NULL
        COMMENT '原始文件 SHA-256（存储落库并回读校验后回填）';
