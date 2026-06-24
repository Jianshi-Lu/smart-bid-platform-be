-- V10: 重构 supplier_detail 表
--
-- 变更内容：
-- 1. 表名 bid_winning_supplier_detail → supplier_detail
-- 2. 主键 detail_id → supplier_id（语义：供应商唯一标识）
-- 3. 删除 notification_id（与 bid_winning_notification 解除关联，改为通过 supplier_detail 找 notification）
-- 4. amount → quote_price（报价）
-- 5. target_email → supplier_contact（联系方式，拓宽为邮箱/电话/微信）
ALTER TABLE bid_winning_supplier_detail
    RENAME TO supplier_detail,
    CHANGE detail_id supplier_id INT NOT NULL AUTO_INCREMENT
        COMMENT '供应商ID（主键）',
    DROP COLUMN notification_id,
    DROP INDEX idx_notification_id,
    CHANGE amount quote_price DECIMAL(15,2) NOT NULL
        COMMENT '报价（元）',
    CHANGE target_email supplier_contact VARCHAR(255)
        COMMENT '供应商联系方式（邮箱/电话/微信）';
