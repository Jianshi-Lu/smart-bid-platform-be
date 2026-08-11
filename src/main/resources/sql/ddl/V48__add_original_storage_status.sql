-- V48: 为知识文件与资信文件补充原始对象存储状态。
-- SHA-256 负责内容完整性；该状态负责区分“尚未写入/已写入/写入失败”，
-- 让前端能够准确判断原文件是否可以下载，并保留上传阶段失败原因。

ALTER TABLE credential_file
    ADD COLUMN original_storage_status VARCHAR(20) NOT NULL DEFAULT 'STORED'
        COMMENT '原始对象存储状态：STORING/STORED/FAILED';

ALTER TABLE knowledge_file
    ADD COLUMN original_storage_status VARCHAR(20) NOT NULL DEFAULT 'STORED'
        COMMENT '原始对象存储状态：STORING/STORED/FAILED';

-- 存量记录按已回填 SHA-256 判断原始对象是否已经完成存储校验。
UPDATE credential_file
SET original_storage_status = CASE
    WHEN original_sha256 IS NOT NULL AND original_sha256 <> '' THEN 'STORED'
    ELSE 'FAILED'
END;

UPDATE knowledge_file
SET original_storage_status = CASE
    WHEN original_sha256 IS NOT NULL AND original_sha256 <> '' THEN 'STORED'
    ELSE 'FAILED'
END;
