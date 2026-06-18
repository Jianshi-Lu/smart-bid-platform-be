-- V6: 为三个业务表增加 target_email 字段
-- 用于邮件通知收件人路由（由创建请求的 targetEmail 字段落地）

-- 1. 经办人表
ALTER TABLE bid_handler_info
    ADD COLUMN target_email VARCHAR(255) DEFAULT NULL COMMENT '邮件通知收件人（由创建请求的 targetEmail 字段落地）';

-- 2. 定标通知主表
ALTER TABLE bid_winning_notification
    ADD COLUMN target_email VARCHAR(255) DEFAULT NULL COMMENT '邮件通知收件人（由创建请求的 targetEmail 字段落地）';

-- 3. 中标结果明细表
ALTER TABLE bid_winning_supplier_detail
    ADD COLUMN target_email VARCHAR(255) DEFAULT NULL COMMENT '邮件通知收件人（由创建请求的 targetEmail 字段落地）';
