-- ===========================================================================
-- RuiWen 后台管理系统 迁移脚本（MySQL 8.0，一次性迁移）
-- 一次性执行即可。如重复执行，"Duplicate column / Duplicate key" 报错可忽略。
-- ===========================================================================

-- 1) users 增加 role / status 列
ALTER TABLE users
    ADD COLUMN role   VARCHAR(16) NOT NULL DEFAULT 'USER'   COMMENT 'USER / ADMIN / SUPER_ADMIN' AFTER nickname,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / BANNED'             AFTER role;

CREATE INDEX ix_users_role       ON users (role);
CREATE INDEX ix_users_status     ON users (status);
CREATE INDEX ix_users_created_at ON users (created_at);

-- 2) system_config：key-value 热更新配置表（注册策略 / 站点公告 / 特性开关）
CREATE TABLE IF NOT EXISTS system_config (
    config_key   VARCHAR(64) NOT NULL,
    config_value TEXT        NOT NULL,
    description  VARCHAR(255) NULL,
    updated_by   BIGINT UNSIGNED NULL,
    updated_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 预置默认注册策略：手机号 + 验证码（已存在则不覆盖）
INSERT INTO system_config (config_key, config_value, description)
VALUES ('registration.policy', '{"enabled":true,"mode":"PHONE_CODE"}', '注册策略：enabled 是否开放注册；mode=EMAIL_PASSWORD(邮箱+密码免验证码) / PHONE_CODE(手机号+验证码)')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- 3) login_logs 补索引（后台审计查询）
CREATE INDEX ix_login_logs_created_at        ON login_logs (created_at);
CREATE INDEX ix_login_logs_status_created_at ON login_logs (status, created_at);
CREATE INDEX ix_login_logs_channel_created_at ON login_logs (channel, created_at);
CREATE INDEX ix_login_logs_identifier        ON login_logs (identifier);

-- ===========================================================================
-- 首个超级管理员 bootstrap（二选一）
--   方式一（推荐）：后端配置 admin.bootstrap.identifier（环境变量 ADMIN_BOOTSTRAP_IDENTIFIER），
--                   启动时由 AdminBootstrapRunner 自动提升该用户为 SUPER_ADMIN。
--   方式二：取消下面两行注释，替换为真实手机号或邮箱后执行。
-- ===========================================================================
-- UPDATE users SET role = 'SUPER_ADMIN', status = 'ACTIVE' WHERE phone  = '13800000000';
-- UPDATE users SET role = 'SUPER_ADMIN', status = 'ACTIVE' WHERE email = 'admin@example.com';
