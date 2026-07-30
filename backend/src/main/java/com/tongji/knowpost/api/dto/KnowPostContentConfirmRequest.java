package com.tongji.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 内容上传确认请求。
 */
public record KnowPostContentConfirmRequest(
        @NotBlank String objectKey,
        @NotBlank String etag,
        @NotNull @Positive Long size,
        @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$", message = "sha256 格式非法") String sha256
) {}
