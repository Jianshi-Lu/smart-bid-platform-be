-- V16: supplier_detail → tenderer_detail + 新增 credentials 字段 + risk_analysis 列同步
--
-- 变更内容：
-- 1. supplier_detail 表重命名为 tenderer_detail
-- 2. supplier_id → tenderer_id
-- 3. supplier_name → tenderer_name
-- 4. supplier_contact → tenderer_contact（保持可空，与 V10 行为一致）
-- 5. 新增 credentials VARCHAR(500) 资信材料文件链接
-- 6. risk_analysis 的 supplier_id → tenderer_id, supplier_name → tenderer_name
--
-- 注意：V10 将 target_email 改为 supplier_contact 时未加 NOT NULL，
-- 故 tenderer_contact 也必须保持可空，否则现有 NULL 数据会触发
-- "Data truncation: Invalid use of NULL value" 错误。

-- Step 1: 独立 RENAME 语句（与后续 CHANGE 分开执行，兼容性更稳）
RENAME TABLE supplier_detail TO tenderer_detail;

-- Step 2: 重命名列（tenderer_contact 不加 NOT NULL，保留 V10 的可空语义）
ALTER TABLE tenderer_detail
    CHANGE supplier_id   tenderer_id   INT NOT NULL AUTO_INCREMENT
        COMMENT '投标人ID（主键，自增）',
    CHANGE supplier_name tenderer_name VARCHAR(100) NOT NULL
        COMMENT '投标人名称',
    CHANGE supplier_contact tenderer_contact VARCHAR(255)
        COMMENT '联系方式（邮箱/电话/微信，允许 NULL）';

-- Step 3: 新增 credentials 列
ALTER TABLE tenderer_detail
    ADD COLUMN credentials VARCHAR(500)
        COMMENT '资信材料文件链接';

-- Step 4: 同步 risk_analysis 表的列名
ALTER TABLE risk_analysis
    CHANGE supplier_id   tenderer_id   INT NOT NULL
        COMMENT '逻辑外键，关联 tenderer_detail.tenderer_id',
    CHANGE supplier_name tenderer_name VARCHAR(255) NOT NULL
        COMMENT '投标人名称（冗余存储）';
