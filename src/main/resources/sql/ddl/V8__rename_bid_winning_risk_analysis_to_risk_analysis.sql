-- V8: 重命名风险分析结果表
-- 旧表名 bid_winning_risk_analysis → 新表名 risk_analysis
-- 字段、索引、COMMENT 保持不变（V7 已定义）
-- 历史建表脚本 V7 保持原样，本脚本仅做重命名迁移
RENAME TABLE bid_winning_risk_analysis TO risk_analysis;
