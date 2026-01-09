package com.tongji.llm.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SpringAiEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embedQuery(String text) {
        if (!StringUtils.hasText(text)) {
            return new float[0];
        }
        return embeddingModel.embed(text);
    }
}
