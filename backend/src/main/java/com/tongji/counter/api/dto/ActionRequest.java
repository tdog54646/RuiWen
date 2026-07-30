package com.tongji.counter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 行为请求体：用于点赞/收藏等操作的实体标识。
 */
@Data
public class ActionRequest {
    @NotBlank
    @Pattern(regexp = "knowpost", message = "不支持的实体类型")
    private String entityType; // 如: knowpost
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "实体 ID 格式非法")
    private String entityId;   // 内容ID
}
