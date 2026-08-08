-- V42: 操作历史 operation 收敛为增删改查枚举并加 CHECK 约束
-- 具体业务阶段（状态变更、文件上传/转换/向量化/重试）不再单独占用 operation 值，
-- 通过 before_snapshot / after_snapshot 的字段差异推导；历史表只记录写操作。

-- 存量数据映射：旧的具体操作值收敛到四类枚举。
UPDATE invitation_for_bid_operation_history
SET operation = 'UPDATE'
WHERE operation = 'STATUS_CHANGE';

UPDATE credential_file_operation_history
SET operation = CASE operation
    WHEN 'UPLOAD' THEN 'CREATE'
    WHEN 'CONVERT' THEN 'UPDATE'
    ELSE operation
    END;

UPDATE knowledge_file_operation_history
SET operation = CASE operation
    WHEN 'UPLOAD' THEN 'CREATE'
    WHEN 'CONVERT' THEN 'UPDATE'
    WHEN 'VECTORIZE' THEN 'UPDATE'
    WHEN 'RETRY' THEN 'UPDATE'
    ELSE operation
    END;

ALTER TABLE invitation_for_bid_operation_history
    ADD CONSTRAINT chk_invitation_history_operation
    CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE'));

ALTER TABLE bid_handler_info_operation_history
    ADD CONSTRAINT chk_bid_handler_history_operation
    CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE'));

ALTER TABLE bid_record_operation_history
    ADD CONSTRAINT chk_bid_record_history_operation
    CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE'));

ALTER TABLE credential_file_operation_history
    ADD CONSTRAINT chk_credential_file_history_operation
    CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE'));

ALTER TABLE knowledge_file_operation_history
    ADD CONSTRAINT chk_knowledge_file_history_operation
    CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE'));
