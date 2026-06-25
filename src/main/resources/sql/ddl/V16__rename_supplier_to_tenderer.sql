-- V16: supplier_detail → tenderer_detail + 新增 credentials 字段 + risk_analysis 列同步
--
-- 变更内容：
-- 1. supplier_detail 表重命名为 tenderer_detail
-- 2. supplier_id → tenderer_id
-- 3. supplier_name → tenderer_name
-- 4. supplier_contact → tenderer_contact
-- 5. 新增 credentials VARCHAR(500) 资信材料文件链接
-- 6. risk_analysis 的 supplier_id → tenderer_id, supplier_name → tenderer_name

-- Step 1: 重命名 supplier_detail 表及列
ALTER TABLE supplier_detail
    RENAME TO tenderer_detail,
    CHANGE supplier_id   tenderer_id   INT NOT NULL AUTO_INCREMENT COMMENT '投标人ID（主键，自增）',
    CHANGE supplier_name tenderer_name VARCHAR(100) NOT NULL COMMENT '投标人名称',
    CHANGE supplier_contact tenderer_contact VARCHAR(255) NOT NULL COMMENT '联系方式（邮箱/电话/微信）';

-- Step 2: 新增 credentials 列
ALTER TABLE tenderer_detail
    ADD COLUMN credentials VARCHAR(500) COMMENT '资信材料文件链接';

-- Step 3: 同步 risk_analysis 表的列名
ALTER TABLE risk_analysis
    CHANGE supplier_id   tenderer_id   INT NOT NULL COMMENT '逻辑外键，关联 tenderer_detail.tenderer_id',
    CHANGE supplier_name tenderer_name VARCHAR(255) NOT NULL COMMENT '投标人名称（冗余存储）';
