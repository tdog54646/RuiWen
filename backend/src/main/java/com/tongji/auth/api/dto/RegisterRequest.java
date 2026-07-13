package com.tongji.auth.api.dto;

import com.tongji.auth.model.IdentifierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 注册请求。
 * <p>
 * 字段：账号类型与值、（可选）验证码、可选密码、是否同意服务条款。
 * <p>
 * 校验规则由当前注册策略决定：
 * <ul>
 *   <li>邮箱 + 密码模式（EMAIL_PASSWORD）：忽略 code，强制校验密码。</li>
 *   <li>手机号 + 验证码模式（PHONE_CODE）：强制校验 code，密码可选。</li>
 * </ul>
 */
public record RegisterRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        String code,
        String password,
        boolean agreeTerms
) {
}
