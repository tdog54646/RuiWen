package com.tongji.llm.rag.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tongji.llm.rag.embedding.EmbeddingService;
import com.tongji.llm.rag.model.RagProperties;
import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.llm.rag.rerank.RerankerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 双路混合检索服务：向量检索 + BM25 全文检索 + RRF 融合。
 * <p>
 * 该服务是整个 RAG 检索链路的核心，负责：
 * <ol>
 *   <li><b>第一路：向量检索（Vector Search）。</b>
 *       将用户查询文本通过 EmbeddingModel 生成稠密向量，
 *       使用 ES 的 <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/knn-search.html">KNN Search</a>
 *       在 dense_vector 字段上做余弦相似度检索。</li>
 *   <li><b>第二路：BM25 关键词检索（Keyword Search）。</b>
 *       使用 ES 的 multi_match 查询在 text 字段（ik_max_word 分词）上做全文检索。
 *       利用中文分词和同义词扩展提升召回率。</li>
 *   <li><b>RRF 融合。</b>
 *       调用 {@link RrfFusionService} 将两路结果按倒数排名融合算法合并，
 *       返回按综合相关性降序排列的 Chunk 列表。</li>
 *   <li><b>可选精排。</b>
 *       调用 {@link RerankerService} 对 RRF 后的 Top 结果做重排。</li>
 * </ol>
 *
 * <h2>性能考虑</h2>
 * <ul>
 *   <li>两路检索相互独立，可以并行执行以降低端到端延迟。
 *       本实现使用顺序执行（简单可靠），若追求极致性能可改用 CompletableFuture 并行化。</li>
 *   <li>两路各自只取 topK（默认 20），避免将整个索引扫描一遍，
 *       同时保证了 RRF 融合时有足够的候选集。</li>
 *   <li>精排默认关闭；若开启，建议 topK=10~20，避免精排模型调用量过大。</li>
 * </ul>
 *
 * @see RrfFusionService
 * @see RerankerService
 * @see EmbeddingService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final ElasticsearchClient es;
    private final EmbeddingService embeddingService;
    private final RrfFusionService rrfFusionService;
    private final RerankerService rerankerService;
    private final RagProperties properties;

    /**
     * ES 索引名，指向 RAG Chunk 向量索引（由 Spring AI VectorStore 初始化或手动创建）。
     * 索引结构参考 resources/es-mapping-rag-chunk.json。
     */
    private static final String INDEX_NAME = "ruiwen-ai-index";

    /**
     * ES 向量字段名，与 es-mapping-rag-chunk.json 中 dense_vector 字段名保持一致。
     */
    private static final String VECTOR_FIELD = "embedding";

    /**
     * ES 文本字段名，用于 BM25 全文检索。
     */
    private static final String TEXT_FIELD = "content";

    /**
     * ES 元数据字段名（metadata 内部字段）。
     */
    private static final String META_FIELD = "metadata";

    // -------------------------------------------------------------------------
    // 公开方法
    // -------------------------------------------------------------------------

    /**
     * 执行混合检索并返回最终排序后的 Chunk 列表。
     *
     * <p>完整流程：
     * <ol>
     *   <li>生成查询向量（向量路）</li>
     *   <li>并行/顺序执行两路检索</li>
     *   <li>RRF 融合两路结果</li>
     *   <li>（可选）Reranker 精排</li>
     *   <li>截取 topK 结果返回</li>
     * </ol>
     *
     * @param query 用户提问文本
     * @param topK  最终返回的 Chunk 数量上限
     * @return 按综合相关性降序排列的 Chunk 列表；若两路均无召回则返回空列表
     */
    public List<RetrievalChunk> hybridSearch(String query, int topK) {
        if (!StringUtils.hasText(query)) {
            log.warn("Hybrid search called with empty query");
            return List.of();
        }

        int retrievalTopK = properties.getRetrieval().getTopK();

        // Step 1: 生成查询向量
        float[] queryVector = embeddingService.embedQuery(query);
        if (queryVector == null || queryVector.length == 0) {
            log.warn("Embedding returned empty vector for query: {}", query);
            return List.of();
        }

        // Step 2: 并行执行两路检索（使用 CompletableFuture 以降低延迟）
        List<RetrievalChunk> vectorResults;
        List<RetrievalChunk> bm25Results;
        try {
            var vectorFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> vectorSearch(queryVector, query, retrievalTopK));
            var bm25Future = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> bm25Search(query, retrievalTopK));

            // join 等待两路都完成
            java.util.concurrent.CompletableFuture.allOf(vectorFuture, bm25Future).join();

            vectorResults = vectorFuture.getNow(List.of());
            bm25Results = bm25Future.getNow(List.of());
        } catch (Exception e) {
            log.error("Parallel retrieval failed, falling back to sequential: {}", e.getMessage());
            vectorResults = vectorSearch(queryVector, query, retrievalTopK);
            bm25Results = bm25Search(query, retrievalTopK);
        }

        log.debug("Retrieval stats - vector: {}, bm25: {}", vectorResults.size(), bm25Results.size());

        // Step 3: RRF 融合
        List<RetrievalChunk> fused = rrfFusionService.fuseResults(vectorResults, bm25Results);

        // Step 4: 可选精排（Reranker）
        if (properties.getRerank().isEnabled() && !fused.isEmpty()) {
            int rerankTopK = properties.getRerank().getTopK();
            List<RetrievalChunk> candidates = fused.stream().limit(rerankTopK).toList();
            try {
                fused = rerankerService.rerank(query, candidates);
            } catch (Exception e) {
                log.warn("Reranker call failed, falling back to RRF result: {}", e.getMessage());
            }
        }

        // Step 5: 截取 topK
        return fused.stream().limit(topK).toList();
    }

    /**
     * 仅执行向量检索（单路），不进行 RRF 融合。
     * 用于调试或仅有向量检索需求的场景。
     *
     * @param query 用户查询文本
     * @param topK 返回数量
     * @return 按余弦相似度降序的 Chunk 列表
     */
    public List<RetrievalChunk> vectorOnlySearch(String query, int topK) {
        float[] vec = embeddingService.embedQuery(query);
        if (vec == null || vec.length == 0) return List.of();
        return vectorSearch(vec, query, topK);
    }

    /**
     * 仅执行 BM25 检索（单路），不进行 RRF 融合。
     *
     * @param query 用户查询文本
     * @param topK 返回数量
     * @return 按 BM25 得分降序的 Chunk 列表
     */
    public List<RetrievalChunk> bm25OnlySearch(String query, int topK) {
        return bm25Search(query, topK);
    }

    // -------------------------------------------------------------------------
    // 向量检索实现
    // -------------------------------------------------------------------------

    /**
     * 第一路：KNN 向量检索。
     * <p>
     * 使用 ES 9.x 的 HNSW 算法（Approximate KNN）在 dense_vector 字段上检索。
     * ES 会自动按余弦相似度降序返回结果。
     * <p>
     * 注：若向量字段配置了 "cosineSimilarity" similarity，
     * 则 ES 内部会做归一化处理，保证检索结果就是余弦相似度。
     *
     * @param queryVector 归一化后的查询向量（float[]，长度 1536）
     * @param queryText   原始查询文本（用于日志和可选的余弦相似度重新计算）
     * @param topK        召回数量
     * @return 按余弦相似度降序的 Chunk 列表
     */
    private List<RetrievalChunk> vectorSearch(float[] queryVector, String queryText, int topK) {
        try {
            // 使用 lambda 形式构造 KNN 查询，与 Spring AI ElasticsearchVectorStore 保持一致
            // queryVector 需要 List<Float>（而非 double[]）
            List<Float> queryVectorList = toFloatList(queryVector);

            SearchResponse<Map<String, Object>> response = es.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .knn(knn -> knn
                                    .field(VECTOR_FIELD)
                                    .queryVector(queryVectorList)
                                    .k(topK)
                                    .numCandidates(Math.max(topK * 2, 50)))
                            .query(q -> q.matchAll(m -> m)),  // 空 query，由 knn 主导
                    getMapTypeRef());

            return parseHits(response.hits().hits(), "vector");
        } catch (Exception e) {
            log.error("Vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // BM25 全文检索实现
    // -------------------------------------------------------------------------

    /**
     * 第二路：BM25 关键词检索。
     * <p>
     * 在 text 字段（配置 ik_max_word 分词器）上执行 multi_match 查询，
     * 同时搜索 title（加权 3x）和 text（加权 1x）。
     * <p>
     * 注：若 ES 索引尚未配置 IK 分词器，此查询退化为标准 standard 分词器，
     * 中文按单字切分，效果会有所下降。建议配合 es-mapping-rag-chunk.json 创建索引。
     *
     * @param query 用户查询文本
     * @param topK  召回数量
     * @return 按 BM25 得分降序的 Chunk 列表
     */
    private List<RetrievalChunk> bm25Search(String query, int topK) {
        try {
            SearchResponse<Map<String, Object>> response = es.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .query(q -> q
                                    .bool(b -> b
                                            // 交叉匹配：查询词在 text 和 title 中同时出现更好
                                            .should(sh -> sh.multiMatch(mm -> mm
                                                    .query(query)
                                                    .fields(TEXT_FIELD, META_FIELD + ".title^3")
                                                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)
                                            ))
                                            // 短语匹配：查询词按顺序连续出现（高精度召回）
                                            .should(sh -> sh.matchPhrase(mp -> mp
                                                    .field(TEXT_FIELD)
                                                    .query(query)
                                                    .boost(2.0f)   // 短语匹配加权
                                            ))
                                            .minimumShouldMatch("1") // 至少满足一个 should 条件
                                    )),
                    getMapTypeRef());

            return parseHits(response.hits().hits(), "bm25");
        } catch (Exception e) {
            log.error("BM25 search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // ES Hit 解析工具
    // -------------------------------------------------------------------------

    /**
     * 将 ES 搜索结果 Hit 列表解析为 RetrievalChunk 列表。
     *
     * @param hits  ES 搜索结果
     * @param source  来源标识（用于日志："vector" 或 "bm25"）
     * @return 解析后的 Chunk 列表
     */
    private List<RetrievalChunk> parseHits(List<Hit<Map<String, Object>>> hits, String source) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        List<RetrievalChunk> chunks = new ArrayList<>(hits.size());
        for (Hit<Map<String, Object>> hit : hits) {
            RetrievalChunk chunk = parseSingleHit(hit);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }

        log.debug("Parsed {} hits from {} search (total ES hits: {})",
                chunks.size(), source, hits.size());
        return chunks;
    }

    /**
     * 解析单个 ES Hit 为 RetrievalChunk。
     * <p>
     * 字段映射（需与 es-mapping-rag-chunk.json 中的字段名保持一致）：
     * <ul>
     *   <li>source["text"]               → chunk.content</li>
     *   <li>source["metadata"]["chunkId"] → chunk.chunkId</li>
     *   <li>source["metadata"]["postId"]  → chunk.postId</li>
     *   <li>source["metadata"]["title"]   → chunk.title</li>
     *   <li>source["metadata"]["position"] → chunk.position</li>
     *   <li>hit.score()                   → 向量相似度分或 BM25 得分（用于 RRF rank 推导）</li>
     * </ul>
     */
    private RetrievalChunk parseSingleHit(Hit<Map<String, Object>> hit) {
        Map<String, Object> source = hit.source();
        if (source == null) return null;

        try {
            String text = asString(source.get(TEXT_FIELD));
            if (!StringUtils.hasText(text)) {
                return null;
            }

            Object metaObj = source.get(META_FIELD);
            Map<String, Object> meta = toStringObjectMap(metaObj);

            String chunkId = asString(meta != null ? meta.get("chunkId") : null);
            String postId = asString(meta != null ? meta.get("postId") : null);
            String title = asString(meta != null ? meta.get("title") : null);
            Integer position = asInteger(meta != null ? meta.get("position") : null);

            if (!StringUtils.hasText(chunkId)) {
                // fallback: 用 _id 作为 chunkId
                chunkId = hit.id() != null ? hit.id() : UUID.randomUUID().toString();
            }

            RetrievalChunk chunk = RetrievalChunk.of(
                    chunkId,
                    postId != null ? postId : "unknown",
                    text,
                    title != null ? title : "",
                    position != null ? position : 0
            );

            // 记录 ES 原生得分（用于调试，不参与 RRF 计算）
            if (hit.score() != null) {
                chunk.setRrfScore(hit.score());
            }

            return chunk;
        } catch (Exception e) {
            log.warn("Failed to parse ES hit [id={}]: {}", hit.id(), e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 类型转换工具
    // -------------------------------------------------------------------------

    /**
     * float[] 转 List&lt;Float&gt;，ES Java Client 9.x 的 KNN queryVector 接受 List&lt;Float&gt;。
     */
    private static List<Float> toFloatList(float[] floats) {
        List<Float> list = new ArrayList<>(floats.length);
        for (float v : floats) {
            list.add(v);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, Object>> getMapTypeRef() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringObjectMap(Object obj) {
        if (obj instanceof Map<?, ?> m) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    result.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer asInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
