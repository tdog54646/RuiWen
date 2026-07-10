package com.tongji.qa.api.dto;

/**
 * 编辑记忆条目请求（字段可选，为 null 表示不改）。
 */
public record MemoryUpdateRequest(
        String category,
        String content,
        Boolean enabled
) {}
