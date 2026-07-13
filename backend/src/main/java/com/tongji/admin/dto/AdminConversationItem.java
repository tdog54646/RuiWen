package com.tongji.admin.dto;

import java.time.Instant;

/**
 * 后台 AI 会话列表项。
 * <p>会话 ID 为雪花算法生成，用 {@code String} 承载以避免前端精度丢失。</p>
 */
public record AdminConversationItem(
        String id,
        Long userId,
        String userNickname,
        String title,
        Integer messageCount,
        Instant lastMessageAt,
        Boolean deleted,
        Instant createdAt
) {
}
