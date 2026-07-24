package com.tongji.storage.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 聊天图片预签名直传请求（不依赖 postId，按用户隔离）。
 *
 * @param contentType 图片 MIME（如 image/png）
 * @param ext         可选扩展名（如 .png），为空时按 contentType 推断
 */
public record ChatImagePresignRequest(
        @NotBlank String contentType,
        String ext
) {}
