-- 用户第三方账号关联表：记录本站用户与 OAuth 提供方（Google 等）的绑定关系。
-- 全量建表脚本见 schema.sql；本文件用于已有库的增量迁移。
CREATE TABLE IF NOT EXISTS user_oauth (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(32) NOT NULL COMMENT 'OAuth 提供方：google / ...',
    provider_user_id VARCHAR(128) NOT NULL COMMENT '提供方用户唯一 ID（Google 为 sub）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_oauth_provider_uid (provider, provider_user_id),
    KEY ix_user_oauth_user (user_id),
    CONSTRAINT fk_user_oauth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
