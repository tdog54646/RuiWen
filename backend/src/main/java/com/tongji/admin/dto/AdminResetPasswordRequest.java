package com.tongji.admin.dto;

/** 管理员重置用户密码请求。newPassword 为空时由系统生成随机密码并返回明文一次。 */
public record AdminResetPasswordRequest(String newPassword) {
}
