package com.tongji.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改知文可见性请求。 */
public record UpdateVisibilityRequest(@NotBlank(message = "可见性不能为空") String visible) {
}
