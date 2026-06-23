-- V7: 创建风险分析结果表
-- 用于存储 AI 模型对中标供应商的风险分析结果
-- 每条记录对应一次分析，同一 detail_id 可有多条（历史追溯）
-- detail_id / notification_id 为逻辑外键，DB 层不强制 FK 约束
CREATE TABLE bid_winning_risk_analysis (
    analysis_id     INT AUTO_INCREMENT PRIMARY KEY,
    detail_id       INT          NOT NULL COMMENT '逻辑外键，关联 bid_winning_supplier_detail.detail_id',
    supplier_name   VARCHAR(255) NOT NULL COMMENT '冗余存储便于查询',
    notification_id INT          NOT NULL COMMENT '逻辑外键，冗余存储便于查询',
    risk_level      VARCHAR(20)  NOT NULL COMMENT 'LOW / MEDIUM / HIGH / CRITICAL',
    risk_score      DECIMAL(3, 2)         COMMENT '0.00-1.00，可选',
    summary         TEXT         NOT NULL COMMENT '风险摘要（模型输出）',
    key_points      TEXT                   COMMENT '关键风险点 JSON 数组',
    suggestions     TEXT                   COMMENT '建议措施',
    raw_response    MEDIUMTEXT   NOT NULL COMMENT '模型原始响应（便于审计/重放）',
    model_name      VARCHAR(50)  NOT NULL COMMENT '如 deepseek-chat',
    prompt_version  VARCHAR(20)           COMMENT 'prompt 模板版本号',
    analyzed_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_detail (detail_id),
    INDEX idx_notification (notification_id),
    INDEX idx_analyzed_at (analyzed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险分析结果表';
