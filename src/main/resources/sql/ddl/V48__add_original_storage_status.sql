-- V48: 暴露原始文件落对象存储的独立状态。
-- convert_status 只描述 Markdown 转换，不再承担原文件存储进度。

ALTER TABLE credential_file
    ADD COLUMN original_storage_status VARCHAR(20) NOT NULL DEFAULT 'STORED'
        COMMENT '原始文件存储状态：STORING/STORED/FAILED';

ALTER TABLE knowledge_file
    ADD COLUMN original_storage_status VARCHAR(20) NOT NULL DEFAULT 'STORED'
        COMMENT '原始文件存储状态：STORING/STORED/FAILED';

UPDATE credential_file
SET original_storage_status = CASE
    WHEN original_file_path IS NULL OR original_file_path = ''
        THEN 'FAILED'
    ELSE 'STORED'
END;

UPDATE knowledge_file
SET original_storage_status = CASE
    WHEN original_file_path IS NULL OR original_file_path = ''
        THEN 'FAILED'
    ELSE 'STORED'
END;
