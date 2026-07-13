package com.tongji.admin.dto;

import jakarta.validation.constraints.NotNull;

/** 更新注册策略请求。 */
public record UpdateRegistrationPolicyRequest(
        boolean enabled,
        @NotNull(message = "注册方式不能为空") String mode
) {
}
