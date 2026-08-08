-- V41: 知识文件操作历史表
-- 记录知识文件的上传/转换/向量化/重试/删除，before/after 为 knowledge_file 整行 JSON 快照；
-- 所有对象存储变更均通过 Java CRUD 服务触发并同步写入本表。

CREATE TABLE IF NOT EXISTS knowledge_file_operation_history (
    history_id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史记录ID',
    document_uuid   CHAR(36) NOT NULL COMMENT '知识文件单元UUID（逻辑外键 → knowledge_file.document_uuid）',
    operation       VARCHAR(20) NOT NULL COMMENT '操作类型：UPLOAD/CONVERT/VECTORIZE/RETRY/DELETE',
    operator_type   VARCHAR(20) NOT NULL COMMENT '操作人类型：BID_INVITER/BIDDER',
    operator_id     BIGINT NULL COMMENT '操作人账号ID',
    before_snapshot JSON NULL COMMENT '变更前整行快照（上传时为 NULL）',
    after_snapshot  JSON NULL COMMENT '变更后整行快照（删除时为 NULL）',
    remark          VARCHAR(500) NULL COMMENT '备注（如失败原因）',
    created_at      DATETIME NOT NULL COMMENT '操作时间',
    PRIMARY KEY (history_id),
    KEY idx_knowledge_file_created (document_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文件操作历史';
