-- V9: 精简 risk_analysis 表字段 + 重命名 detail_id → supplier_id
--
-- 变更内容：
-- 1. detail_id 改名为 supplier_id（与 supplier_detail.supplier_id 关联）
-- 2. 删除 notification_id（通过 supplier_detail 可间接关联）
-- 3. 删除 risk_score（与 risk_level 重复）
-- 4. 删除 key_points（summary 已覆盖关键信息）
-- 5. 删除 model_name（项目目前只用 deepseek-chat）
-- 6. 重建索引：删 idx_detail/idx_notification，建 idx_supplier
ALTER TABLE risk_analysis
    CHANGE detail_id supplier_id INT NOT NULL
        COMMENT '逻辑外键，关联 supplier_detail.supplier_id',
    DROP COLUMN notification_id,
    DROP COLUMN risk_score,
    DROP COLUMN key_points,
    DROP COLUMN model_name,
    DROP INDEX idx_detail,
    DROP INDEX idx_notification,
    ADD INDEX idx_supplier (supplier_id);
