-- V18: 全面重命名为 bid 规范 + tenderer_detail 合并为 bid_record
--
-- 范围：
-- 1. 表重命名：tender_notice → invitation_for_bid（"Invitation for Bids"，招标公告）
-- 2. 表重命名：tender_handler_info → bid_handler_info（招标经办人，保留 bid 命名）
-- 3. 表重命名：tender_notice_status_history → invitation_for_bid_status_history
-- 4. 表重命名：tenderer_detail → bid_record（投标记录，合并原 tenderer_detail）
-- 5. invitation_for_bid 列重命名：notice_id → invitation_id，tender_handler_id → bid_handler_id
-- 6. invitation_for_bid_status_history 列重命名：notice_id → invitation_id
-- 7. bid_record 旧字段重命名：tenderer_id → bid_record_id（PK），tenderer_name → bidder_name，tenderer_contact → bidder_contact
-- 8. bid_record 新增 3 列：invitation_id / bid_no / bid_at
-- 9. bid_record 加 UNIQUE (invitation_id, bidder_name, bid_no) + INDEX (invitation_id)
-- 10. risk_analysis FK 列重命名：tenderer_id → bid_record_id，tenderer_name → bidder_name
--
-- 前置操作（手动执行，DDL 不负责数据迁移）：
--   TRUNCATE TABLE risk_analysis;
--   TRUNCATE TABLE tenderer_detail;
-- 然后再执行本 DDL（执行顺序：先 truncate，再 rename，rename 自动覆盖约束索引）。


TRUNCATE TABLE risk_analysis;
TRUNCATE TABLE tenderer_detail;

RENAME TABLE
    tender_notice                TO invitation_for_bid,
    tender_handler_info          TO bid_handler_info,
    tender_notice_status_history TO invitation_for_bid_status_history,
    tenderer_detail              TO bid_record;

ALTER TABLE invitation_for_bid
    CHANGE notice_id          invitation_id INT NOT NULL AUTO_INCREMENT
        COMMENT '招标公告ID（主键，自增；对应 World Bank Invitation for Bids）',
    CHANGE tender_handler_id  bid_handler_id INT NOT NULL
        COMMENT '招标经办人ID（逻辑外键 → bid_handler_info.bid_handler_id）';

ALTER TABLE invitation_for_bid_status_history
    CHANGE notice_id invitation_id INT NOT NULL
        COMMENT '招标公告ID（逻辑外键 → invitation_for_bid.invitation_id）';

-- Step 4: bid_record 列重命名（仅 CHANGE，不 ADD —— 为 Step 5 准备锚点列与被引用列）
ALTER TABLE bid_record
    CHANGE tenderer_id      bid_record_id    INT NOT NULL AUTO_INCREMENT
        COMMENT '投标记录ID（主键，自增）',
    CHANGE tenderer_name    bidder_name      VARCHAR(100) NOT NULL
        COMMENT '投标人名称（World Bank 术语：Bidder）',
    CHANGE tenderer_contact bidder_contact   VARCHAR(255)
        COMMENT '联系方式（邮箱/电话/微信，允许 NULL）';

-- Step 5: bid_record 新增 3 列 + UNIQUE + INDEX
-- （此时 bid_record_id / bidder_name 已存在，AFTER 锚点与 UNIQUE 引用列均有效）
ALTER TABLE bid_record
    ADD COLUMN invitation_id INT NOT NULL
        COMMENT '招标公告ID（逻辑外键 → invitation_for_bid.invitation_id）' AFTER bid_record_id,
    ADD COLUMN bid_no INT NOT NULL DEFAULT 1
        COMMENT '投标次数（同 invitation_id + bidder_name 下递增，允许一人多次投标）' AFTER invitation_id,
    ADD COLUMN bid_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '投标时间' AFTER bid_no,
    ADD UNIQUE KEY uk_attempt (invitation_id, bidder_name, bid_no),
    ADD KEY idx_invitation (invitation_id);

ALTER TABLE risk_analysis
    CHANGE tenderer_id   bid_record_id INT NOT NULL
        COMMENT '逻辑外键，关联 bid_record.bid_record_id',
    CHANGE tenderer_name bidder_name   VARCHAR(255) NOT NULL
        COMMENT '投标人名称（冗余存储）';
