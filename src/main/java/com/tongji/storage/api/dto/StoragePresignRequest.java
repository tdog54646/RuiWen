package com.tongji.storage.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 预签名直传请求。
 */
public record StoragePresignRequest(
        @NotBlank String scene, // knowpost_content | knowpost_image
        @NotBlank String postId, // 字符串避免前端精度丢失，分布式id
        @NotBlank String contentType,
        String ext
) {}