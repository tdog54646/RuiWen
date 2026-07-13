package com.tongji.admin.dto;

import java.time.Instant;

/**
 * 后台用户记忆列表项（AI 记忆）。ID 为雪花，用 String 承载。
 */
public record AdminMemoryItem(
        String id,
        Long userId,
        String userNickname,
        String category,
        String content,
        String source,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
