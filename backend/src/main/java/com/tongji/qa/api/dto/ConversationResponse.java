package com.tongji.qa.api.dto;

import com.tongji.qa.model.QaConversation;

import java.time.Instant;

/**
 * 会话响应。
 * <p>id 为字符串化的雪花 ID，避免前端 JS 精度丢失（与项目既有约定一致）。
 */
public record ConversationResponse(
        String id,
        String title,
        Integer messageCount,
        Instant lastMessageAt,
        Instant createdAt
) {
    public static ConversationResponse of(QaConversation c) {
        return new ConversationResponse(
                String.valueOf(c.getId()),
                c.getTitle(),
                c.getMessageCount(),
                c.getLastMessageAt(),
                c.getCreatedAt()
        );
    }
}
