-- V11: risk_analysis.raw_response 类型从 MEDIUMTEXT 改为 JSON
--
-- 变更原因：
--   1. 数据语义更明确：raw_response 本质就是 JSON 格式
--   2. 启用 JSON 路径查询：JSON_EXTRACT(raw_response, '$.riskLevel')
--   3. DB 层强制 JSON 校验，避免脏数据落库
--
-- 前置检查（必须先确认全表数据为合法 JSON）：
--   SELECT COUNT(*) FROM risk_analysis WHERE NOT JSON_VALID(raw_response);
--   -- 该查询结果必须为 0，否则 ALTER 会失败
--
-- 兼容性说明：
--   - Java 代码无改动（MySQL JSON 列在 JDBC 层面被当作 String 处理）
--   - 现有 MEDIUMTEXT 数据需为合法 JSON 才能 ALTER
--   - 改后 INSERT 必须为合法 JSON，否则 MySQL 抛 SQLException
--
-- 索引说明：
--   - JSON 列无法直接建 B-tree 索引（除非建"生成列"）
--   - 当前查询模式是 SELECT 全行，不需要 JSON 列上的索引
--   - 未来如需按 riskLevel 查询可加：
--     ALTER TABLE risk_analysis
--     ADD COLUMN risk_level_idx VARCHAR(20) AS (JSON_UNQUOTE(JSON_EXTRACT(raw_response, '$.riskLevel'))) STORED,
--     ADD INDEX idx_risk_level (risk_level_idx);

ALTER TABLE risk_analysis
    MODIFY COLUMN raw_response JSON NOT NULL
        COMMENT '模型原始响应 JSON（V11 由 MEDIUMTEXT 改为 JSON）';