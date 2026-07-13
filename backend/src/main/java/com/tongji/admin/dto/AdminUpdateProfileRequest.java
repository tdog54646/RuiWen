package com.tongji.admin.dto;

import java.time.LocalDate;

/** 管理员编辑用户资料请求（复用 UserService.updateProfile）。 */
public record AdminUpdateProfileRequest(
        String nickname,
        String bio,
        String gender,
        LocalDate birthday,
        String school,
        String zgId,
        String avatar,
        String email
) {
}
