package com.tongji.qa.api.dto;

/**
 * 多轮流式问答请求体。
 *
 * @param conversationId 会话 ID（字符串化的雪花 ID）；为空时自动新建会话
 * @param question       用户提问（必填）
 * @param topK           RAG 检索 Chunk 数量上限（可选）
 * @param maxTokens      LLM 最大生成 token 数（可选）
 * @param scope          检索范围：all（公开+我的私有）或 private（仅我的私有）；为空默认 all
 */
public record QaChatRequest(
        String conversationId,
        String question,
        Integer topK,
        Integer maxTokens,
        String scope
) {}
