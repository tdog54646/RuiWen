package com.tongji.knowpost.api.dto;

/**
 * 热点问答接口响应。
 */
public record HotQuestionResponse(
        long postId,
        String question
) {}
