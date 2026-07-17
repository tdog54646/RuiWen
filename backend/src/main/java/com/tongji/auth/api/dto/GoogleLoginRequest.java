package com.tongji.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Google 登录请求。
 *
 * @param idToken 前端 GIS 回调拿到的 Google ID Token（credential）。
 */
public record GoogleLoginRequest(@NotBlank(message = "ID Token 不能为空") String idToken) {
}
