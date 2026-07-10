package com.tongji.qa.api.dto;

/**
 * 新建会话请求体。
 *
 * @param title 会话标题（可选，为空时使用默认标题）
 */
public record ConversationCreateRequest(String title) {}
