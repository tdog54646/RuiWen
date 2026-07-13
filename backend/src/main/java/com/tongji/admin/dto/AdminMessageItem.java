package com.tongji.admin.dto;

import java.time.Instant;

/**
 * 后台 AI 消息项（会话详情内查看）。ID 为雪花，用 String 承载。
 */
public record AdminMessageItem(
        String id,
        Long conversationId,
        Long userId,
        String role,
        String content,
        String status,
        Instant createdAt
) {
}
