-- V6: 为三个业务表增加联系方式字段
-- 用于邮件通知收件人路由（由创建请求的 tendererContact 字段落地）
--
-- 注意：本脚本历史版本引用 bid_winning_supplier_detail.target_email
-- V10 重构后表名改为 supplier_detail，列名改为 supplier_contact
-- V16 重命名后 supplier_detail → tenderer_detail，supplier_contact → tenderer_contact

-- 1. 经办人表
ALTER TABLE bid_handler_info
    ADD COLUMN target_email VARCHAR(255) DEFAULT NULL COMMENT '邮件通知收件人（由创建请求的 targetEmail 字段落地）';

-- 2. 定标通知主表
ALTER TABLE bid_winning_notification
    ADD COLUMN target_email VARCHAR(255) DEFAULT NULL COMMENT '邮件通知收件人（由创建请求的 targetEmail 字段落地）';

-- 3. 供应商明细表（原 bid_winning_supplier_detail，V10 重构后更名为 supplier_detail，V16 再更名为 tenderer_detail）
ALTER TABLE supplier_detail
    ADD COLUMN supplier_contact VARCHAR(255) DEFAULT NULL COMMENT '供应商联系方式（邮箱/电话/微信，V16 重命名为 tenderer_contact）';
