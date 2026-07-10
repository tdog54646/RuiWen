package com.tongji.qa.api.dto;

import com.tongji.qa.model.UserMemory;

import java.time.Instant;

/**
 * 用户记忆条目响应。
 */
public record MemoryResponse(
        String id,
        String category,
        String content,
        String source,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static MemoryResponse of(UserMemory m) {
        return new MemoryResponse(
                String.valueOf(m.getId()),
                m.getCategory(),
                m.getContent(),
                m.getSource(),
                m.getEnabled(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
