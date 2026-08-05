-- V28: chat_conversation 增加标题来源字段
-- title_source 区分自动生成（AUTO）与用户手动修改（MANUAL）；
-- 自动标题只在 title_source='AUTO' 时写入，避免覆盖用户手动改名。

USE chat_agent;

ALTER TABLE chat_conversation
    ADD COLUMN title_source VARCHAR(20) NOT NULL DEFAULT 'AUTO'
    COMMENT '标题来源：AUTO 自动生成 / MANUAL 用户手动修改';
