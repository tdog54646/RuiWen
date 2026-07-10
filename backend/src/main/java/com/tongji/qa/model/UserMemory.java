package com.tongji.qa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 用户记忆条目（结构化）。
 * <p>source=auto 为 AI 自动总结生成，source=manual 为用户手动维护。
 * 重新生成时仅删除 auto 条目，manual 条目保留。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemory {
    private Long id;
    private Long userId;
    /** 分类，如 专业领域 / 偏好 / 已知事实 */
    private String category;
    private String content;
    /** auto / manual */
    private String source;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
