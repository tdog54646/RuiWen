package com.tongji.qa.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 会话重命名请求体。
 */
public record ConversationRenameRequest(@NotBlank String title) {}
