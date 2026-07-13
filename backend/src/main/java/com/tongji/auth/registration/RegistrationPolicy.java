package com.tongji.auth.registration;

/**
 * 注册策略。
 *
 * @param enabled 是否开放注册（关闭后任何标识都无法注册）。
 * @param mode    注册方式（邮箱+密码 / 手机号+验证码）。
 */
public record RegistrationPolicy(boolean enabled, RegistrationMode mode) {

    /** 系统配置表中存储该策略的 key。 */
    public static final String CONFIG_KEY = "registration.policy";

    /** 默认策略：开放注册、手机号 + 验证码。 */
    public static RegistrationPolicy defaultValue() {
        return new RegistrationPolicy(true, RegistrationMode.PHONE_CODE);
    }
}
