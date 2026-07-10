package com.tongji.qa.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户新增记忆条目请求（source 固定为 manual）。
 */
public record MemoryCreateRequest(
        String category,
        @NotBlank String content
) {}
