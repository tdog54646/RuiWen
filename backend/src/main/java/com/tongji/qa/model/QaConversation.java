package com.tongji.qa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 问答会话实体。
 * <p>每用户可拥有多个会话（多会话模型），会话内承载多轮消息历史。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaConversation {
    private Long id;
    private Long userId;
    private String title;
    private Integer messageCount;
    private Instant lastMessageAt;
    /** 软删除标记：0=正常，1=已删除 */
    private Boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
}
