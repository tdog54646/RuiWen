package com.tongji.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改用户角色请求。 */
public record UpdateRoleRequest(@NotBlank(message = "角色不能为空") String role) {
}
