package com.tongji.admin.dto;

import java.time.Duration;

/**
 * 系统配置快照（只读展示 + 部分可改项）。
 */
public record SystemSettingsResponse(
        PasswordPolicy password,
        Verification verification,
        Jwt jwt,
        Registration registration,
        String announcement
) {

    public record PasswordPolicy(int minLength, int bcryptStrength) {
    }

    public record Verification(int codeLength, Duration ttl, int maxAttempts, Duration sendInterval, int dailyLimit) {
    }

    public record Jwt(Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Registration(boolean enabled, String mode) {
    }
}
