-- V23: 统一凭证文件批次和文件的状态字段命名

ALTER TABLE credential_file_batch
    CHANGE COLUMN batch_status status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING'
        COMMENT '状态：UPLOADING/UPLOADED/CONVERTING/CONVERTED/FAILED',
    DROP INDEX idx_batch_status,
    ADD KEY idx_status (status);

ALTER TABLE credential_file
    CHANGE COLUMN convert_status status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED'
        COMMENT '状态：UPLOADED/CONVERTING/CONVERTED/FAILED',
    DROP INDEX idx_convert_status,
    ADD KEY idx_status (status);
