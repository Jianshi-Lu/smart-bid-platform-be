-- V22: 创建资信文件上传批次表
-- 一个 bid_record 可以拥有多个上传批次；每个 upload_batch_id 表示一套不可变的资信材料快照。
-- bid_record_id 为逻辑外键，项目当前不在数据库层建立 FOREIGN KEY 约束。

CREATE TABLE credential_file_batch (
    upload_batch_id       CHAR(36) NOT NULL COMMENT '资信文件上传批次 UUID',
    bid_record_id         INT NOT NULL COMMENT '逻辑关联 bid_record.bid_record_id',
    batch_no              INT NOT NULL COMMENT '该投标记录下的资信材料批次序号，从 1 开始',

    batch_status          VARCHAR(20) NOT NULL DEFAULT 'UPLOADING'
                          COMMENT '批次状态：UPLOADING/UPLOADED/CONVERTING/CONVERTED/FAILED',
    file_count            INT NOT NULL DEFAULT 0 COMMENT '本批次原始文件数量',
    converted_file_count  INT NOT NULL DEFAULT 0 COMMENT '本批次已完成 Markdown 转换的文件数量',
    error_message         VARCHAR(1000) NULL COMMENT '批次级上传或转换失败原因',

    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '批次创建时间',
    uploaded_at           DATETIME NULL COMMENT '本批次原始文件全部上传并校验完成时间',
    converting_at         DATETIME NULL COMMENT '本批次开始 Markdown 转换时间',
    converted_at          DATETIME NULL COMMENT '本批次全部 Markdown 转换完成时间',
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP COMMENT '批次最后更新时间',

    PRIMARY KEY (upload_batch_id),
    UNIQUE KEY uk_bid_record_batch_no (bid_record_id, batch_no),
    KEY idx_bid_record_id (bid_record_id),
    KEY idx_batch_status (batch_status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='资信文件上传批次表';
