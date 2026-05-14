package com.tongji.llm.rag.rerank;


import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.llm.rag.search.HybridSearchService;
import lombok.extern.slf4j.Slf4j;
import com.tongji.llm.rag.model.RagProperties.Rerank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reranker（精排模型）服务接口与默认实现。
 * <p>
 * Reranker 位于 RRF 融合之后，对 Top N 候选 Chunk 做更精细的相关性排序。
 * 与向量检索和 BM25 相比，Reranker 模型（如 BGE-Reranker、Cohere Rerank）
 * 能够同时考虑查询与文档的深层语义关系，因此在精排阶段效果通常优于单路召回。
 *
 * <h2>使用流程</h2>
 * <pre>
 * // 1. 混合检索召回 Top 20
 * List&lt;RetrievalChunk&gt; candidates = hybridSearchService.hybridSearch(query, 20);
 *
 * // 2. 精排截取 Top 5
 * List&lt;RetrievalChunk&gt; reranked = rerankerService.rerank(query, candidates);
 * </pre>
 *
 * <h2>接入外部 Reranker</h2>
 * <p>
 * 若要接入真实的 Reranker 模型（如 BGE-Reranker），可：
 * <ol>
 *   <li>创建 {@code BgeRerankerService} 实现本接口。</li>
 *   <li>在 {@code BgeRerankerService} 上添加
 *       {@code @ConditionalOnProperty(name = "rag.rerank.enabled", havingValue = "true")}
 *       注解，使其在启用时才生效。</li>
 *   <li>在 application.yml 中配置 {@code rag.rerank.enabled=true} 以及 API 地址。</li>
 * </ol>
 *
 * @see HybridSearchService
 * @see com.tongji.llm.rag.model.RagProperties.Rerank
 */
@Slf4j
@Service
public class RerankerService {

    /**
     * 对候选 Chunk 列表进行精排。
     * <p>
     * 默认实现（恒等映射）：直接按原顺序返回 input 列表，不做任何重排。
     * 当 {@code rag.rerank.enabled=true} 时，应替换为真实 Reranker 模型调用。
     *
     * @param query     用户查询文本
     * @param chunks   候选 Chunk 列表（通常来自 RRF 融合后的 Top 结果）
     * @param <T>      继承自 RetrievalChunk 的具体类型
     * @return 重排后的 Chunk 列表。长度通常不超过 input 长度。
     */
    public <T extends RetrievalChunk> List<RetrievalChunk> rerank(String query, List<T> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        // 默认恒等实现：透传 RRF 排序结果
        // 接入真实 Reranker 后，此处改为 HTTP 调用模型 API
        log.debug("Reranker passthrough: {} chunks (rerank not enabled)", chunks.size());
        return List.copyOf(chunks);
    }

    /**
     * 带最大返回数量限制的精排。
     *
     * @param query  用户查询文本
     * @param chunks 候选 Chunk 列表
     * @param topK   最大返回数量
     * @param <T>    Chunk 类型
     * @return 精排后截取 TopK 的结果
     */
    public <T extends RetrievalChunk> List<RetrievalChunk> rerank(String query, List<T> chunks, int topK) {
        return rerank(query, chunks).stream().limit(topK).toList();
    }
}
