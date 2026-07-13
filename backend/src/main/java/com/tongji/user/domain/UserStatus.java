package com.tongji.user.domain;

/**
 * 用户状态枚举。
 * <p>
 * 取值与数据库 {@code users.status} 列一致：
 * <ul>
 *   <li>{@link #ACTIVE}：正常（默认）。</li>
 *   <li>{@link #BANNED}：封禁，禁止登录与刷新令牌。</li>
 * </ul>
 */
public final class UserStatus {

    public static final String ACTIVE = "ACTIVE";
    public static final String BANNED = "BANNED";

    private UserStatus() {
    }

    /**
     * 判断状态字符串是否合法。
     *
     * @param status 状态字符串。
     * @return 是否为合法状态。
     */
    public static boolean isValid(String status) {
        return ACTIVE.equals(status) || BANNED.equals(status);
    }
}
