package com.tongji.qa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 问答消息实体。
 * <p>持久化用户提问（role=user）与 AI 回答（role=assistant），支持历史回看与多轮记忆。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaMessage {
    private Long id;
    private Long conversationId;
    /** 冗余字段，便于按用户聚合（如记忆总结回看最近 N 条） */
    private Long userId;
    /** user / assistant */
    private String role;
    private String content;
    /** streaming / completed / interrupted / error */
    private String status;
    private Instant createdAt;
}
