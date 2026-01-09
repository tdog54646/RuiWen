package com.tongji.llm.rag;

public interface EmbeddingService {
    /**
     * 为查询文本生成向量（建议返回原始向量，由上层做归一化）。
     */
    float[] embedQuery(String text);
}
