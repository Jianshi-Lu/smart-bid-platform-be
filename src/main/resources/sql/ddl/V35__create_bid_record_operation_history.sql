-- V35: 投标记录操作历史表
-- 记录投标记录的创建/修改/删除，before/after 为整行 JSON 快照；
-- 招标方与投标方（/bidder/bid-records/*）的操作都会写入本表。

CREATE TABLE IF NOT EXISTS bid_record_operation_history (
    history_id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史记录ID',
    bid_record_id   INT NOT NULL COMMENT '投标记录ID（逻辑外键）',
    operation       VARCHAR(20) NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE',
    operator_type   VARCHAR(20) NOT NULL COMMENT '操作人类型：BID_INVITER/BIDDER',
    operator_id     BIGINT NULL COMMENT '操作人账号ID',
    before_snapshot JSON NULL COMMENT '变更前整行快照（创建时为 NULL）',
    after_snapshot  JSON NULL COMMENT '变更后整行快照（删除时为 NULL）',
    remark          VARCHAR(500) NULL COMMENT '备注',
    created_at      DATETIME NOT NULL COMMENT '操作时间',
    PRIMARY KEY (history_id),
    KEY idx_bid_record_created (bid_record_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投标记录操作历史';
