package com.tongji.admin.dto;

/** 更新系统配置请求（字段均可选，仅传需要修改的）。 */
public record UpdateSystemSettingsRequest(
        /** 密码最小长度（可为空表示不修改）。 */
        Integer passwordMinLength,
        /** 站点公告（可为空表示不修改）。 */
        String announcement
) {
}
