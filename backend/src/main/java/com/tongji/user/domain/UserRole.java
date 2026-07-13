package com.tongji.user.domain;

/**
 * 用户角色枚举。
 * <p>
 * 取值与数据库 {@code users.role} 列一致：
 * <ul>
 *   <li>{@link #USER}：普通用户（默认）。</li>
 *   <li>{@link #ADMIN}：管理员，可访问后台管理功能。</li>
 *   <li>{@link #SUPER_ADMIN}：超级管理员，拥有全部权限（含改角色、系统配置等敏感操作）。</li>
 * </ul>
 */
public final class UserRole {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private UserRole() {
    }

    /**
     * 判断角色字符串是否合法。
     *
     * @param role 角色字符串。
     * @return 是否为合法角色。
     */
    public static boolean isValid(String role) {
        return USER.equals(role) || ADMIN.equals(role) || SUPER_ADMIN.equals(role);
    }

    /**
     * 判断该角色是否具有后台管理权限（ADMIN 或 SUPER_ADMIN）。
     *
     * @param role 角色字符串。
     * @return 是否为管理员。
     */
    public static boolean isAdmin(String role) {
        return ADMIN.equals(role) || SUPER_ADMIN.equals(role);
    }
}
