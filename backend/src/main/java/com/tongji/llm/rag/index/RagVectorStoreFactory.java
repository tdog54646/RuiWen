package com.tongji.llm.rag.index;

import lombok.RequiredArgsConstructor;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 为稳定别名或指定版本索引创建 Spring AI VectorStore。 */
@Component
@RequiredArgsConstructor
public class RagVectorStoreFactory {

    private final RestClient restClient;
    private final EmbeddingModel embeddingModel;
    private final RagIndexManager indexManager;
    private final Map<String, VectorStore> stores = new ConcurrentHashMap<>();

    public VectorStore activeStore() {
        return forIndex(indexManager.readAlias());
    }

    public VectorStore forIndex(String indexName) {
        return stores.computeIfAbsent(indexName, this::build);
    }

    private VectorStore build(String indexName) {
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setIndexName(indexName);
        options.setDimensions(indexManager.dimensions());
        options.setEmbeddingFieldName("embedding");
        options.setSimilarity(SimilarityFunction.cosine);
        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(false)
                .build();
    }
}
