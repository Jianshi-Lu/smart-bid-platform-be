-- V12: 重命名 bid_winning_notification → tender_notice + 主键列重命名
--
-- 变更内容：
-- 1. 表名：bid_winning_notification → tender_notice
-- 2. 主键列：notification_id → notice_id
-- 3. 索引 idx_staff_id（handler_id）保留（不依赖列名）
--
-- 前置检查（执行前确认表存在）：
--   SELECT COUNT(*) FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = 'bid-winning' AND TABLE_NAME = 'bid_winning_notification';
--   -- 结果必须为 1，否则 RENAME 会失败
--
-- 兼容性说明：
-- - 本项目使用逻辑外键（DB 无 FK 约束），RENAME 不影响业务代码
-- - 如其他系统有硬编码表名，需同步更新
-- - Java 实体已同步改为 TenderNotice（noticeId 字段）

ALTER TABLE bid_winning_notification
    RENAME TO tender_notice,
    RENAME COLUMN notification_id TO notice_id;
