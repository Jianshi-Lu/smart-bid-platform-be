-- V33: 招标公告操作历史表
-- 记录公告的创建/修改/删除/状态变更，before/after 为整行 JSON 快照。

CREATE TABLE IF NOT EXISTS invitation_for_bid_operation_history (
    history_id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史记录ID',
    invitation_id   INT NOT NULL COMMENT '招标公告ID（逻辑外键）',
    operation       VARCHAR(20) NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/STATUS_CHANGE',
    operator_type   VARCHAR(20) NOT NULL COMMENT '操作人类型：BID_INVITER/BIDDER',
    operator_id     BIGINT NULL COMMENT '操作人账号ID',
    before_snapshot JSON NULL COMMENT '变更前整行快照（创建时为 NULL）',
    after_snapshot  JSON NULL COMMENT '变更后整行快照（删除时为 NULL）',
    remark          VARCHAR(500) NULL COMMENT '备注（如状态变更原因）',
    created_at      DATETIME NOT NULL COMMENT '操作时间',
    PRIMARY KEY (history_id),
    KEY idx_invitation_created (invitation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招标公告操作历史';
