-- V40: 资信文件操作历史表
-- 记录资信文件的上传/转换/删除，before/after 为 credential_file 整行 JSON 快照；
-- 所有对象存储变更均通过 Java CRUD 服务触发并同步写入本表。

CREATE TABLE IF NOT EXISTS credential_file_operation_history (
    history_id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史记录ID',
    credential_file_id  BIGINT NOT NULL COMMENT '资信文件ID（逻辑外键 → credential_file.file_id）',
    operation           VARCHAR(20) NOT NULL COMMENT '操作类型：UPLOAD/CONVERT/DELETE',
    operator_type       VARCHAR(20) NOT NULL COMMENT '操作人类型：BID_INVITER/BIDDER',
    operator_id         BIGINT NULL COMMENT '操作人账号ID',
    before_snapshot     JSON NULL COMMENT '变更前整行快照（上传时为 NULL）',
    after_snapshot      JSON NULL COMMENT '变更后整行快照（删除时为 NULL）',
    remark              VARCHAR(500) NULL COMMENT '备注（如转换失败原因）',
    created_at          DATETIME NOT NULL COMMENT '操作时间',
    PRIMARY KEY (history_id),
    KEY idx_credential_file_created (credential_file_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资信文件操作历史';
