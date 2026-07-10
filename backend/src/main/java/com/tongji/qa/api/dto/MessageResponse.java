package com.tongji.qa.api.dto;

import com.tongji.qa.model.QaMessage;

import java.time.Instant;

/**
 * 消息响应（用户提问 / AI 回答）。
 */
public record MessageResponse(
        String id,
        String role,
        String content,
        String status,
        Instant createdAt
) {
    public static MessageResponse of(QaMessage m) {
        return new MessageResponse(
                String.valueOf(m.getId()),
                m.getRole(),
                m.getContent(),
                m.getStatus(),
                m.getCreatedAt()
        );
    }
}
