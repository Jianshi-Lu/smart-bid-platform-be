-- V15: tender_notice 加 status 字段 + 新建 tender_notice_status_history 表
--
-- 变更内容：
-- 1. tender_notice 新增 status 列（VARCHAR(20)，默认 'DRAFT'）
-- 2. 新建 tender_notice_status_history 表（状态变更审计）

ALTER TABLE tender_notice
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/CLOSED/AWARDED/CANCELLED';

CREATE TABLE tender_notice_status_history (
    history_id INT NOT NULL AUTO_INCREMENT COMMENT '历史记录ID（主键）',
    notice_id INT NOT NULL COMMENT '招标公告ID（逻辑外键，关联 tender_notice.notice_id）',
    from_status VARCHAR(20) COMMENT '变更前状态（首次为 null）',
    to_status VARCHAR(20) NOT NULL COMMENT '变更后状态',
    remark VARCHAR(255) COMMENT '变更备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    PRIMARY KEY (history_id),
    KEY idx_notice_id (notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招标公告状态变更历史';
