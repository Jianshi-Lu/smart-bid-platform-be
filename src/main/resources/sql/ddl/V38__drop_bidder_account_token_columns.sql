-- V38: 彻底移除令牌方案遗留列（V36 创建，V37 起已弃用）
-- 当前鉴权为 HTTP Basic 实时校验，不建会话、不发令牌。

ALTER TABLE bidder_account
    DROP COLUMN token,
    DROP COLUMN token_expires_at;
