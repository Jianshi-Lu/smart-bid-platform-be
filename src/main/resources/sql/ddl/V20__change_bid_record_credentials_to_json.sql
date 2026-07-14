-- V20: credentials 从单个字符串路径改为 Markdown 文件路径 JSON 数组
-- 当前库中的 bid_record / risk_analysis 仅包含测试数据，本次直接清空，不做历史数据迁移。

TRUNCATE TABLE risk_analysis;
TRUNCATE TABLE bid_record;

ALTER TABLE bid_record
    MODIFY COLUMN credentials JSON NULL
        COMMENT '转换后 Markdown 文件路径数组，顺序与 credential_file.file_order 一致';
