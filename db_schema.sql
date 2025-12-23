CREATE DATABASE IF NOT EXISTS emperorgame CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE emperorgame;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    nickname VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL DEFAULT NULL,
    last_login_ip VARCHAR(64) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_nickname (nickname)
);

-- 注册日志：同一 IP 每日限制
CREATE TABLE IF NOT EXISTS registration_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ip VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    register_date DATE AS (DATE(created_at)) STORED,
    PRIMARY KEY (id),
    KEY idx_ip_date (ip, register_date)
);

-- 登录日志
CREATE TABLE IF NOT EXISTS login_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED DEFAULT NULL,
    nickname VARCHAR(64) NOT NULL,
    ip VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_ip_date (ip, created_at)
);

-- 会话 Token 表：存储登录发放的签名 token
CREATE TABLE IF NOT EXISTS session_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id VARCHAR(64) NOT NULL,
    token VARCHAR(512) NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_token (token),
    KEY idx_account (account_id)
);

