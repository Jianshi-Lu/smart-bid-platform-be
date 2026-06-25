-- V17: 重命名 risk_analysis 索引 idx_supplier → idx_tenderer
--
-- V16 已将 supplier_id 改为 tenderer_id，但漏改了索引名。
-- 索引名是纯 DB 内部标识，不影响 Java 代码。

ALTER TABLE risk_analysis
    RENAME INDEX idx_supplier TO idx_tenderer;
