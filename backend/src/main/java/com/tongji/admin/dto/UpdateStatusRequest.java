package com.tongji.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改用户状态（封禁/解封）请求。 */
public record UpdateStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}
