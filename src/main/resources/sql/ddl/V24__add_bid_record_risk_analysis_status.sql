-- V24: 增加投标记录级风险分析状态，防止同一投标记录并发调用大模型。

ALTER TABLE bid_record
    ADD COLUMN risk_analysis_status VARCHAR(20) NOT NULL DEFAULT 'IDLE'
        COMMENT '风险分析状态：IDLE/ANALYZING/COMPLETED/FAILED',
    ADD COLUMN risk_analysis_started_at DATETIME NULL
        COMMENT '最近一次风险分析开始时间',
    ADD COLUMN risk_analysis_error VARCHAR(1000) NULL
        COMMENT '最近一次风险分析失败原因';
