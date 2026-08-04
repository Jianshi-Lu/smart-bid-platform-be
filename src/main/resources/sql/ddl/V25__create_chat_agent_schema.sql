-- V25: 创建 chat-agent 独立 Database/Schema
-- MySQL 中 DATABASE 与 SCHEMA 是同义概念。
-- 本脚本需要具备 CREATE 权限的初始化账号执行；不负责创建应用账号或分配密码。

CREATE DATABASE chat_agent
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chat_agent;
