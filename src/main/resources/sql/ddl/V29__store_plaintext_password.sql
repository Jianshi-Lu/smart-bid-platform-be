-- V29: 演示环境最小鉴权——密码改明文存储（去掉 PBKDF2 加盐哈希）
-- 注意：明文密码仅适用于本地演示，禁止在对外环境使用；
-- 恢复哈希时需同时回退 Service 校验逻辑与前端凭证存储方式。

ALTER TABLE bidder_account
    CHANGE COLUMN password_hash password VARCHAR(255) NOT NULL
        COMMENT '登录密码（演示环境明文，禁止生产使用）';
