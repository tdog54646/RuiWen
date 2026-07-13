package com.tongji.auth.api.dto;

/**
 * 注册策略公开响应（供注册页首屏匿名读取）。
 *
 * @param enabled 是否开放注册。
 * @param mode    注册方式：EMAIL_PASSWORD（邮箱+密码，免验证码）/ PHONE_CODE（手机号+验证码）。
 */
public record RegistrationConfigResponse(boolean enabled, String mode) {
}
