package com.tongji.admin.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 后台用户详情（管理员可见联系方式与密码状态）。
 */
public record AdminUserDetail(
        Long id,
        String nickname,
        String phone,
        String email,
        String role,
        String status,
        String avatar,
        String bio,
        String gender,
        LocalDate birthday,
        String school,
        String zgId,
        boolean hasPassword,
        Instant createdAt,
        Instant updatedAt
) {
}
