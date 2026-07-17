package com.tongji.auth.oauth;

/**
 * Google 用户身份（从校验通过的 ID Token 中解析）。
 *
 * @param sub           Google 用户唯一 ID。
 * @param email         邮箱。
 * @param emailVerified 邮箱是否已通过 Google 验证。
 * @param name          昵称（可能为空）。
 * @param picture       头像 URL（可能为空）。
 */
public record GoogleIdentity(String sub, String email, boolean emailVerified, String name, String picture) {
}
