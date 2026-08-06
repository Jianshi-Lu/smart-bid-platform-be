-- V21: 创建资信文件表
-- 每个原始上传文件对应一行；file_uuid 是文件单元的唯一标识，
-- 同时作为对象存储目录（{bidderAccountId}/files/{file_uuid}/...）的一部分。
-- bid_record_id 在投标记录创建成功后回填，当前阶段允许为空。

CREATE TABLE credential_file (
    file_id              BIGINT NOT NULL AUTO_INCREMENT COMMENT '资信文件ID',
    file_uuid            CHAR(36) NOT NULL COMMENT '文件单元UUID，由 Java 后端生成',
    bid_record_id        INT NULL COMMENT '逻辑关联 bid_record.bid_record_id，提交投标后回填',

    file_order            INT NOT NULL DEFAULT 0 COMMENT '文件顺序（同一次上传请求内）',
    original_filename     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type          VARCHAR(100) NULL COMMENT '文件 MIME 类型',
    file_size             BIGINT NOT NULL COMMENT '原始文件大小，字节',
    sha256                CHAR(64) NOT NULL COMMENT '原始文件 SHA-256',

    storage_type          VARCHAR(20) NOT NULL DEFAULT 'LOCAL'
                           COMMENT '存储类型：LOCAL/MINIO',
    original_file_path    VARCHAR(500) NOT NULL COMMENT '原始文件物理存储相对路径',
    markdown_file_path    VARCHAR(500) NULL COMMENT '转换后 Markdown 文件物理存储相对路径',

    convert_status        VARCHAR(20) NOT NULL DEFAULT 'UPLOADED'
                           COMMENT '转换状态：UPLOADED/CONVERTING/CONVERTED/FAILED',
    error_message         VARCHAR(1000) NULL COMMENT '转换失败原因',

    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '文件记录创建时间',
    converted_at          DATETIME NULL COMMENT 'Markdown 转换完成时间',
    bound_at              DATETIME NULL COMMENT '与 bid_record 绑定时间',

    PRIMARY KEY (file_id),
    KEY idx_file_uuid (file_uuid),
    KEY idx_bid_record_id (bid_record_id),
    KEY idx_convert_status (convert_status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='投标资信文件表';
