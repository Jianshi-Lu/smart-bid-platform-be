-- V13: 重命名 bid_handler_info → tender_handler_info + 主键列重命名
--
-- 变更内容：
-- 1. 表名：bid_handler_info → tender_handler_info
-- 2. 主键列：handler_id → tender_handler_id
-- 3. tender_notice.handler_id → tender_handler_id（逻辑外键同步）
-- 4. 索引 idx_staff_id（tender_handler_id）保留（不依赖列名）
--
-- 前置检查（执行前确认表存在）：
--   SELECT COUNT(*) FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = 'bid-winning' AND TABLE_NAME = 'bid_handler_info';
--   -- 结果必须为 1，否则 RENAME 会失败
--
-- 兼容性说明：
-- - 本项目使用逻辑外键（DB 无 FK 约束），RENAME 不影响业务代码
-- - tender_notice.handler_id 同步 RENAME，保持 FK 引用一致性
-- - Java 实体已同步改为 TenderHandlerInfo（tenderHandlerId 字段）

ALTER TABLE bid_handler_info
    RENAME TO tender_handler_info,
    RENAME COLUMN handler_id TO tender_handler_id;

ALTER TABLE tender_notice
    RENAME COLUMN handler_id TO tender_handler_id;
