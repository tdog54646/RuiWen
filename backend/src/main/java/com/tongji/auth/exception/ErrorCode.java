package com.tongji.auth.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    IDENTIFIER_EXISTS("IDENTIFIER_EXISTS", "账号已存在"),
    IDENTIFIER_NOT_FOUND("IDENTIFIER_NOT_FOUND", "账号不存在"),
    ZGID_EXISTS("ZGID_EXISTS", "Line ID已存在"),
    VERIFICATION_RATE_LIMIT("VERIFICATION_RATE_LIMIT", "验证码发送过于频繁"),
    VERIFICATION_DAILY_LIMIT("VERIFICATION_DAILY_LIMIT", "验证码发送次数超限"),
    VERIFICATION_NOT_FOUND("VERIFICATION_NOT_FOUND", "验证码不存在或已过期"),
    VERIFICATION_MISMATCH("VERIFICATION_MISMATCH", "验证码错误"),
    VERIFICATION_TOO_MANY_ATTEMPTS("VERIFICATION_TOO_MANY_ATTEMPTS", "验证码尝试次数过多"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "登录凭证错误"),
    PASSWORD_POLICY_VIOLATION("PASSWORD_POLICY_VIOLATION", "密码强度不足"),
    TERMS_NOT_ACCEPTED("TERMS_NOT_ACCEPTED", "请先同意服务条款"),
    REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", "刷新令牌无效"),
    BAD_REQUEST("BAD_REQUEST", "请求参数错误"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误"),
    USER_BANNED("USER_BANNED", "账号已被封禁"),
    FORBIDDEN("FORBIDDEN", "无操作权限"),
    LAST_SUPER_ADMIN("LAST_SUPER_ADMIN", "不能降级或封禁最后一个超级管理员"),
    REGISTRATION_DISABLED("REGISTRATION_DISABLED", "注册功能已关闭"),
    REGISTRATION_MODE_MISMATCH("REGISTRATION_MODE_MISMATCH", "当前注册模式不支持该方式"),
    OAUTH_TOKEN_INVALID("OAUTH_TOKEN_INVALID", "第三方登录凭证无效"),
    OAUTH_EMAIL_NOT_VERIFIED("OAUTH_EMAIL_NOT_VERIFIED", "该 Google 账号邮箱未验证");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
