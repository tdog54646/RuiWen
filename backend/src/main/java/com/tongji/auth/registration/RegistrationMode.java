package com.tongji.auth.registration;

/**
 * 注册方式枚举（管理员可在后台手动切换）。
 * <ul>
 *   <li>{@link #EMAIL_PASSWORD}：邮箱 + 密码注册，<b>无需验证码</b>。</li>
 *   <li>{@link #PHONE_CODE}：手机号 + 验证码注册，密码可选。</li>
 * </ul>
 * 注意：本系统仅支持两种模式二选一，不提供“同时开放”。
 */
public enum RegistrationMode {
    /** 邮箱 + 密码（免验证码）。 */
    EMAIL_PASSWORD,
    /** 手机号 + 验证码。 */
    PHONE_CODE;

    public static RegistrationMode fromString(String value) {
        if (value == null) {
            return PHONE_CODE;
        }
        return switch (value.trim().toUpperCase()) {
            case "EMAIL_PASSWORD", "EMAIL" -> EMAIL_PASSWORD;
            case "PHONE_CODE", "PHONE" -> PHONE_CODE;
            default -> PHONE_CODE;
        };
    }
}
