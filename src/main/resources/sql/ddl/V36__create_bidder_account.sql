-- V36: 投标方账号表 + bid_record 账号归属列（登录身份系统）
-- bid_record / credential_file 通过 bidder_account_id 溯源归属；
-- 存量投标记录 bidder_account_id 为 NULL，保持招标方代录流程可用。

CREATE TABLE IF NOT EXISTS bidder_account (
    bidder_account_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '投标方账号ID',
    username          VARCHAR(64) NOT NULL COMMENT '登录账号（唯一）',
    password_hash     VARCHAR(255) NOT NULL COMMENT '密码哈希（PBKDF2）',
    phone             VARCHAR(20) NULL COMMENT '联系电话（可选）',
    display_name      VARCHAR(100) NULL COMMENT '显示名称',
    token             VARCHAR(64) NULL COMMENT '当前登录令牌（简单会话）',
    token_expires_at  DATETIME NULL COMMENT '登录令牌过期时间',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
    PRIMARY KEY (bidder_account_id),
    UNIQUE KEY uk_bidder_account_username (username)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='投标方账号表';

ALTER TABLE bid_record
    ADD COLUMN bidder_account_id BIGINT NULL
        COMMENT '投标方账号ID（逻辑外键 → bidder_account.bidder_account_id）'
        AFTER bidder_name;

ALTER TABLE bid_record
    ADD KEY idx_bidder_account_id (bidder_account_id);
