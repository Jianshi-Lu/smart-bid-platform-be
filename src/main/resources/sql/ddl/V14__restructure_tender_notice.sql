-- V14: 重构 tender_notice 字段（对标招标公告业务场景）
--
-- 变更内容：
-- 1. 删除：target_cost / bidding_status / target_email / document_fee
-- 2. 新增：has_document_fee（TINYINT(1)，从 document_fee 迁移数据）
-- 3. 重命名：bidding_process → tender_process
-- 4. 重命名：bidding_method → tender_method
-- 5. 重命名：tech_bid_deadline → technical_tender_deadline
-- 6. 重命名：business_bid_deadline → business_tender_deadline
-- 7. 新增：purchase_company / purchase_department / tendering_party
--
-- 注意：document_fee 是 DECIMAL(10,2)，直接 CHANGE 到 TINYINT(1) 会因
-- 已有数据值过大导致 Out of range 错误。分三步：先加新列 → 迁移数据 → 删旧列。

-- Step 1: 先加 has_document_fee 新列
ALTER TABLE tender_notice
    ADD COLUMN has_document_fee TINYINT(1) COMMENT '标书费（1有 0无）';

-- Step 2: 迁移已有数据
UPDATE tender_notice SET has_document_fee = 1 WHERE document_fee > 0;
UPDATE tender_notice SET has_document_fee = 0 WHERE document_fee <= 0 OR document_fee IS NULL;

-- Step 3: 删旧列 + 其他变更
ALTER TABLE tender_notice
    DROP COLUMN target_cost,
    DROP COLUMN bidding_status,
    DROP COLUMN target_email,
    DROP COLUMN document_fee,
    CHANGE COLUMN bidding_process tender_process VARCHAR(50) COMMENT '招标流程',
    CHANGE COLUMN bidding_method tender_method VARCHAR(50) COMMENT '招标方式',
    CHANGE COLUMN tech_bid_deadline technical_tender_deadline DATETIME COMMENT '技术回标截止时间',
    CHANGE COLUMN business_bid_deadline business_tender_deadline DATETIME COMMENT '商务回标截止时间',
    ADD COLUMN purchase_company   VARCHAR(100) COMMENT '采购公司',
    ADD COLUMN purchase_department VARCHAR(100) COMMENT '采购部门',
    ADD COLUMN tendering_party     VARCHAR(100) COMMENT '招标主体';
