package com.tongji.storage.api.dto;

import java.util.Map;

/**
 * OSS 表单直传授权响应（putUrl 为兼容既有前端保留的上传地址字段名）。
 */
public record StoragePresignResponse(
        String objectKey,
        String putUrl,
        String objectUrl,
        String readUrl,
        String method,
        Map<String, String> headers,
        Map<String, String> formFields,
        int expiresIn
) {}
