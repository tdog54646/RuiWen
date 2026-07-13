package com.tongji.admin.dto;

import java.time.Instant;

/**
 * 后台用户列表项。
 */
public record AdminUserListItem(
        Long id,
        String nickname,
        String phone,
        String email,
        String role,
        String status,
        String avatar,
        Instant createdAt
) {
}
