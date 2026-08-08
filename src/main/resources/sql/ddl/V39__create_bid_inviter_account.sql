-- V39: 招标方（管理员）账号表 + 初始管理员账号
-- 与投标方账号体系对称：注册/登录/me 走 HTTP Basic 实时校验。
-- 演示环境密码明文存储，与 bidder_account 保持一致；禁止用于生产。

CREATE TABLE IF NOT EXISTS bid_inviter_account (
    bid_inviter_account_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '招标方账号ID',
    username             VARCHAR(64) NOT NULL COMMENT '登录账号（唯一）',
    password             VARCHAR(255) NOT NULL COMMENT '登录密码（演示环境明文，禁止生产使用）',
    phone                VARCHAR(20) NULL COMMENT '联系电话（可选）',
    display_name         VARCHAR(100) NULL COMMENT '显示名称',
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
    PRIMARY KEY (bid_inviter_account_id),
    UNIQUE KEY uk_bid_inviter_account_username (username)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='招标方（管理员）账号表';

-- 初始管理员账号：admin / admin（幂等插入）
INSERT INTO bid_inviter_account (username, password, phone, display_name, created_at)
SELECT 'admin', 'admin', NULL, '平台管理员', NOW()
WHERE NOT EXISTS (SELECT 1 FROM bid_inviter_account WHERE username = 'admin');
