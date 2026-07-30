package com.tongji.knowpost.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 已发布知文编辑请求：正文已上传到对象存储后，连同元数据一次性提交。
 */
public record KnowPostUpdateRequest(
        @NotBlank String objectKey,
        @NotBlank String etag,
        @NotNull @Positive Long size,
        @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$", message = "sha256 格式非法") String sha256,
        @NotBlank String title,
        Long tagId,
        @Size(max = 20) List<String> tags,
        @Size(max = 20) List<String> imgUrls,
        String visible,
        Boolean isTop,
        String description
) {}
