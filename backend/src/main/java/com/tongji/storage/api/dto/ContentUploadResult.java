package com.tongji.storage.api.dto;

/**
 * 服务端直传正文到 OSS 的结果（用于 AI 录入文章）。
 *
 * @param objectKey OSS 对象键
 * @param etag      OSS 返回的 ETag
 * @param size      字节大小
 * @param sha256    SHA-256 十六进制校验和
 */
public record ContentUploadResult(
        String objectKey,
        String contentUrl,
        String etag,
        long size,
        String sha256
) {}
