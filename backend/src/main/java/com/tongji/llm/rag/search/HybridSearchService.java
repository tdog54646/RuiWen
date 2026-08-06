package com.tongji.llm.rag.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tongji.llm.rag.RetrievalContext;
import com.tongji.llm.rag.embedding.EmbeddingService;
import com.tongji.llm.rag.index.RagIndexManager;
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
    private final RagIndexManager indexManager;

    /**
     * ES 索引名，指向 RAG Chunk 向量索引（由 Spring AI VectorStore 初始化或手动创建）。
     * 索引结构参考 resources/es-mapping-rag-chunk.json。
     */
    /** ES 向量字段名，与版本化 mapping 中 dense_vector 字段名保持一致。 */
    private static final String VECTOR_FIELD = "embedding";

    /**
     * ES 文本字段名，用于 BM25 全文检索。
     */
    private static final String TEXT_FIELD = "content";

    /**
     * ES 元数据字段名（metadata 内部字段）。
     */
    private static final String META_FIELD = "metadata";

    /** metadata.visible 字段路径（用户隔离过滤用）。 */
    private static final String META_VISIBLE = META_FIELD + ".visible";

    /** metadata.creatorId 字段路径（用户隔离过滤用）。 */
    private static final String META_CREATOR = META_FIELD + ".creatorId";

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
     * @param ctx   检索隔离上下文（用户 + scope），用于在两路检索层过滤
     * @return 按综合相关性降序排列的 Chunk 列表；若两路均无召回则返回空列表
     */
    public List<RetrievalChunk> hybridSearch(String query, int topK, RetrievalContext ctx) {
        int finalTopK = Math.min(Math.max(1, topK), properties.getPrompt().getContextLimit());
        int retrievalTopK = Math.max(finalTopK, properties.getRetrieval().getTopK());
        RetrievalOptions options = new RetrievalOptions(
                retrievalTopK,
                Math.max(retrievalTopK, properties.getRetrieval().getKnnCandidates()),
                properties.getRrf().getK(),
                Math.max(finalTopK, properties.getRerank().getTopK()),
                finalTopK,
                properties.getRerank().isEnabled(),
                properties.getRetrieval().getTitleBoost(),
                properties.getRetrieval().getPhraseBoost(),
                properties.getRetrieval().isDiversityEnabled(),
                properties.getRetrieval().getNearDuplicateThreshold());
        return search(query, ctx, options).chunks();
    }

    /**
     * 使用显式参数执行完整检索并返回分阶段轨迹。生产问答与离线评测共用此方法，
     * 避免评测脚本复制出一套与线上不一致的算法。
     */
    public RetrievalSearchResult search(String query, RetrievalContext ctx, RetrievalOptions options) {
        if (!StringUtils.hasText(query)) {
            log.warn("Hybrid search called with empty query");
            RetrievalTrace trace = new RetrievalTrace(
                    query, indexManager.readAlias(), options,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    new RetrievalTrace.Latency(0, 0, 0, 0, 0, 0),
                    false, "empty-query");
            return new RetrievalSearchResult(List.of(), trace);
        }

        // Step 1: 生成查询向量
        long embeddingStarted = System.nanoTime();
        float[] queryVector = embeddingService.embedQuery(query);
        long embeddingMs = elapsedMs(embeddingStarted);
        return searchWithVector(query, ctx, options, queryVector, embeddingMs);
    }

    /** 离线网格评测复用同一查询向量，避免参数组合重复调用 Embedding API。 */
    public RetrievalSearchResult searchWithVector(
            String query,
            RetrievalContext ctx,
            RetrievalOptions options,
            float[] queryVector,
            long embeddingMs) {
        if (!StringUtils.hasText(query)) {
            return search(query, ctx, options);
        }
        long retrievalStarted = System.nanoTime();
        int retrievalTopK = options.retrievalTopK();
        // 用户隔离 filter：两路检索共用，在召回阶段即过滤，RRF/rerank 只对已过滤结果排序
        Query isolationFilter = ctx == null
                ? Query.of(q -> q.matchAll(m -> m))
                : buildIsolationFilter(ctx);

        if (queryVector == null || queryVector.length == 0) {
            log.warn("Embedding returned empty vector for query: {}", query);
            RetrievalTrace trace = new RetrievalTrace(
                    query, indexManager.readAlias(), options,
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    new RetrievalTrace.Latency(embeddingMs, 0, 0, 0, 0, embeddingMs),
                    false, "empty-embedding");
            return new RetrievalSearchResult(List.of(), trace);
        }

        // Step 2: 并行执行两路检索（使用 CompletableFuture 以降低延迟）
        TimedResults vectorTimed;
        TimedResults bm25Timed;
        try {
            var vectorFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> timed(() -> vectorSearch(queryVector, query, retrievalTopK,
                            options.knnCandidates(), isolationFilter, ctx)));
            var bm25Future = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> timed(() -> bm25Search(query, retrievalTopK, isolationFilter, ctx,
                            bm25Options(options.titleBoost(), options.phraseBoost()))));

            // join 等待两路都完成
            java.util.concurrent.CompletableFuture.allOf(vectorFuture, bm25Future).join();

            vectorTimed = vectorFuture.join();
            bm25Timed = bm25Future.join();
        } catch (Exception e) {
            log.error("Parallel retrieval failed, falling back to sequential: {}", e.getMessage());
            vectorTimed = timed(() -> vectorSearch(queryVector, query, retrievalTopK,
                    options.knnCandidates(), isolationFilter, ctx));
            bm25Timed = timed(() -> bm25Search(query, retrievalTopK, isolationFilter, ctx,
                    bm25Options(options.titleBoost(), options.phraseBoost())));
        }

        List<RetrievalChunk> vectorResults = vectorTimed.results();
        List<RetrievalChunk> bm25Results = bm25Timed.results();
        List<RetrievalTrace.RankedChunk> vectorSnapshot =
                RetrievalTrace.snapshot(vectorResults, RetrievalTrace.Score.SOURCE);
        List<RetrievalTrace.RankedChunk> bm25Snapshot =
                RetrievalTrace.snapshot(bm25Results, RetrievalTrace.Score.SOURCE);

        log.debug("Retrieval stats - vector: {}, bm25: {}", vectorResults.size(), bm25Results.size());

        // Step 3: RRF 融合
        long fusionStarted = System.nanoTime();
        List<RetrievalChunk> fused = rrfFusionService.fuseResults(
                vectorResults, bm25Results, options.rrfK(), properties.getRetrieval().getMinScore());
        long fusionMs = elapsedMs(fusionStarted);
        List<RetrievalTrace.RankedChunk> fusedSnapshot =
                RetrievalTrace.snapshot(fused, RetrievalTrace.Score.RRF);

        // Step 4: 可选精排（Reranker）
        List<RetrievalChunk> ranked = fused;
        List<RetrievalTrace.RankedChunk> rerankedSnapshot = List.of();
        boolean rerankerApplied = false;
        String fallbackReason = null;
        long rerankMs = 0;
        if (options.rerankEnabled() && !fused.isEmpty()) {
            List<RetrievalChunk> candidates = fused.stream().limit(options.rerankTopK()).toList();
            long rerankStarted = System.nanoTime();
            try {
                if (!rerankerService.isAvailable()) {
                    throw new IllegalStateException("Reranker 配置不完整");
                }
                ranked = rerankerService.rerank(query, candidates);
                rerankerApplied = true;
                rerankedSnapshot = RetrievalTrace.snapshot(ranked, RetrievalTrace.Score.RERANK);
            } catch (Exception e) {
                fallbackReason = e.getMessage();
                if (!properties.getRerank().isFailOpen()) {
                    throw new IllegalStateException("Reranker 调用失败且禁止降级", e);
                }
                ranked = fused;
                log.warn("Reranker call failed, falling back to RRF result: {}", fallbackReason);
            }
            rerankMs = elapsedMs(rerankStarted);
        }

        // Step 5: 截取 topK
        List<RetrievalChunk> selected = selectFinal(ranked, options);
        RetrievalTrace.Score selectedScore = rerankerApplied
                ? RetrievalTrace.Score.RERANK : RetrievalTrace.Score.RRF;
        RetrievalTrace trace = new RetrievalTrace(
                query,
                indexManager.readAlias(),
                options,
                vectorSnapshot,
                bm25Snapshot,
                fusedSnapshot,
                rerankedSnapshot,
                RetrievalTrace.snapshot(selected, selectedScore),
                new RetrievalTrace.Latency(
                        embeddingMs,
                        vectorTimed.elapsedMs(),
                        bm25Timed.elapsedMs(),
                        fusionMs,
                        rerankMs,
                        embeddingMs + elapsedMs(retrievalStarted)),
                rerankerApplied,
                fallbackReason);
        return new RetrievalSearchResult(selected, trace);
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
        int candidates = Math.max(topK, properties.getRetrieval().getKnnCandidates());
        return vectorSearch(vec, query, topK, candidates, Query.of(q -> q.matchAll(m -> m)), null);
    }

    /**
     * 仅执行 BM25 检索（单路），不进行 RRF 融合。
     *
     * @param query 用户查询文本
     * @param topK 返回数量
     * @return 按 BM25 得分降序的 Chunk 列表
     */
    public List<RetrievalChunk> bm25OnlySearch(String query, int topK) {
        return bm25Search(query, topK, Query.of(q -> q.matchAll(m -> m)), null,
                bm25Options(
                        properties.getRetrieval().getTitleBoost(),
                        properties.getRetrieval().getPhraseBoost()));
    }

    /** 使用显式参数执行 BM25，供离线调优复用生产查询构造。 */
    public List<RetrievalChunk> bm25OnlySearch(
            String query, int topK, RetrievalContext ctx, Bm25QueryOptions options) {
        Query filter = ctx == null
                ? Query.of(q -> q.matchAll(m -> m))
                : buildIsolationFilter(ctx);
        return bm25Search(query, topK, filter, ctx, options);
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
    private List<RetrievalChunk> vectorSearch(float[] queryVector, String queryText, int topK, int numCandidates,
                                              Query filter, RetrievalContext ctx) {
        try {
            SearchRequest request = buildVectorSearchRequest(queryVector, topK, numCandidates, filter,
                    indexManager.readAlias());
            SearchResponse<Map<String, Object>> response = es.search(request, getMapTypeRef());
            return parseHits(response.hits().hits(), "vector", ctx);
        } catch (Exception e) {
            log.error("Vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 构造纯 KNN 请求。不要在顶层追加 {@code query: match_all}：
     * Elasticsearch 会将顶层 query 与 knn 按 OR 合并，导致 query 分支绕过 knn.filter。
     */
    static SearchRequest buildVectorSearchRequest(float[] queryVector, int topK, Query filter) {
        return buildVectorSearchRequest(queryVector, topK, Math.max(topK * 2, 50), filter,
                "ruiwen-ai-read");
    }

    static SearchRequest buildVectorSearchRequest(
            float[] queryVector,
            int topK,
            int numCandidates,
            Query filter,
            String indexName) {
        List<Float> queryVectorList = toFloatList(queryVector);
        return SearchRequest.of(s -> s
                .index(indexName)
                .size(topK)
                .knn(knn -> knn
                        .field(VECTOR_FIELD)
                        .queryVector(queryVectorList)
                        .k(topK)
                        .numCandidates(Math.max(topK, numCandidates))
                        .filter(filter)));
    }

    // -------------------------------------------------------------------------
    // BM25 全文检索实现
    // -------------------------------------------------------------------------

    /**
     * 第二路：BM25 关键词检索。
     * <p>
     * 在 content 与 title 字段上执行 multi_match 查询，并可选增加正文、标题短语匹配。
     * <p>
     * 注：若 ES 索引尚未配置 IK 分词器，此查询退化为标准 standard 分词器，
     * 中文按单字切分，效果会有所下降。建议配合 es-mapping-rag-chunk.json 创建索引。
     *
     * @param query 用户查询文本
     * @param topK  召回数量
     * @return 按 BM25 得分降序的 Chunk 列表
     */
    private List<RetrievalChunk> bm25Search(
            String query, int topK, Query filter, RetrievalContext ctx,
            Bm25QueryOptions options) {
        try {
            SearchResponse<Map<String, Object>> response = es.search(s -> s
                            .index(indexManager.readAlias())
                            .size(topK)
                            .query(buildBm25Query(query, filter, options)),
                    getMapTypeRef());

            return parseHits(response.hits().hits(), "bm25", ctx);
        } catch (Exception e) {
            log.error("BM25 search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    static Query buildBm25Query(String query, Query filter, Bm25QueryOptions options) {
        return Query.of(q -> q.bool(b -> {
            b.should(sh -> sh.multiMatch(mm -> mm
                    .query(query)
                    .fields(TEXT_FIELD, META_FIELD + ".title^" + options.titleBoost())
                    .type(textQueryType(options.queryType()))
                    .minimumShouldMatch(options.minimumShouldMatch())
                    .fuzziness("NONE".equals(options.fuzziness()) ? null : options.fuzziness())));
            if (options.phraseBoost() > 0) {
                float contentPhraseBoost = (float) options.phraseBoost();
                float titlePhraseBoost = (float) (options.phraseBoost()
                        * Math.max(1.0, options.titleBoost()));
                b.should(sh -> sh.matchPhrase(mp -> mp
                        .field(TEXT_FIELD)
                        .query(query)
                        .slop(options.phraseSlop())
                        .boost(contentPhraseBoost)));
                b.should(sh -> sh.matchPhrase(mp -> mp
                        .field(META_FIELD + ".title")
                        .query(query)
                        .slop(options.phraseSlop())
                        .boost(titlePhraseBoost)));
            }
            return b.minimumShouldMatch("1").filter(filter);
        }));
    }

    private Bm25QueryOptions bm25Options(double titleBoost, double phraseBoost) {
        return new Bm25QueryOptions(
                titleBoost,
                phraseBoost,
                properties.getRetrieval().getPhraseSlop(),
                properties.getRetrieval().getMinimumShouldMatch(),
                properties.getRetrieval().getQueryType(),
                properties.getRetrieval().getFuzziness());
    }

    private static co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType textQueryType(
            String value) {
        return switch (value) {
            case "most_fields" -> co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.MostFields;
            case "cross_fields" -> co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.CrossFields;
            default -> co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields;
        };
    }

    private List<RetrievalChunk> selectFinal(
            List<RetrievalChunk> ranked, RetrievalOptions options) {
        if (!options.diversityEnabled()) {
            return ranked.stream().limit(options.finalTopK()).toList();
        }
        List<RetrievalChunk> selected = new ArrayList<>(options.finalTopK());
        for (RetrievalChunk candidate : ranked) {
            boolean duplicate = selected.stream().anyMatch(existing ->
                    ContentSimilarity.isNearDuplicate(
                            existing.getContent(), candidate.getContent(),
                            options.nearDuplicateThreshold()));
            if (!duplicate) selected.add(candidate);
            if (selected.size() == options.finalTopK()) break;
        }
        return List.copyOf(selected);
    }

    // -------------------------------------------------------------------------
    // 用户隔离 filter 构造
    // -------------------------------------------------------------------------

    /**
     * 按 {@link RetrievalContext} 构造用户隔离 filter，KNN pre-filter 与 BM25 bool.filter 共用。
     * <ul>
     *   <li>{@code PRIVATE}：{@code creatorId==me AND visible!=public}</li>
     *   <li>{@code ALL}：{@code visible==public} OR（{@code creatorId==me AND visible!=public}）</li>
     * </ul>
     * 缺少 visible 的旧文档不再对其他用户放行；仅作者本人可命中。
     */
    private Query buildIsolationFilter(RetrievalContext ctx) {
        Query termPublic = Query.of(q -> q.term(t -> t.field(META_VISIBLE).value(FieldValue.of("public"))));
        Query notPublic = Query.of(q -> q.bool(b -> b.mustNot(termPublic)));
        Query termMine = Query.of(q -> q.term(t -> t.field(META_CREATOR).value(FieldValue.of(String.valueOf(ctx.userId())))));
        Query mineNonPublic = Query.of(q -> q.bool(b -> b.filter(termMine).filter(notPublic)));

        if (ctx.scope() == RetrievalContext.Scope.PRIVATE) {
            return mineNonPublic;
        }
        // ALL：公共 OR 我的非公开。mineNonPublic 会覆盖“我的旧文档缺 visible”场景。
        return Query.of(q -> q.bool(b -> b
                .should(termPublic)
                .should(mineNonPublic)
                .minimumShouldMatch("1")));
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
    private List<RetrievalChunk> parseHits(List<Hit<Map<String, Object>>> hits, String source,
                                           RetrievalContext ctx) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        List<RetrievalChunk> chunks = new ArrayList<>(hits.size());
        int rejected = 0;
        for (Hit<Map<String, Object>> hit : hits) {
            if (ctx != null && !isSourceAuthorized(hit.source(), ctx)) {
                rejected++;
                continue;
            }
            RetrievalChunk chunk = parseSingleHit(hit);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }

        if (rejected > 0) {
            log.warn("Rejected {} unauthorized hits from {} search for user {} in {} scope",
                    rejected, source, ctx.userId(), ctx.scope());
        }
        log.debug("Parsed {} hits from {} search (total ES hits: {})",
                chunks.size(), source, hits.size());
        return chunks;
    }

    /**
     * 结果级权限兜底。正常情况下 KNN/BM25 的 ES filter 已完成隔离；
     * 此处防止查询构造回归或异常响应把越权文档带入 RRF、Prompt 与推荐来源。
     */
    static boolean isSourceAuthorized(Map<String, Object> source, RetrievalContext ctx) {
        if (source == null || ctx == null) {
            return ctx == null;
        }

        Map<String, Object> meta = toStringObjectMap(source.get(META_FIELD));
        String visible = asString(meta.get("visible"));
        String creatorId = asString(meta.get("creatorId"));
        boolean isPublic = "public".equals(visible);
        boolean isMine = String.valueOf(ctx.userId()).equals(creatorId);

        if (ctx.scope() == RetrievalContext.Scope.PRIVATE) {
            return isMine && !isPublic;
        }

        // 与 buildIsolationFilter 的 ALL 语义一致：公共或我的非公开；缺元数据默认拒绝其他用户。
        return isPublic || (isMine && !isPublic);
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

    private static long elapsedMs(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static TimedResults timed(java.util.function.Supplier<List<RetrievalChunk>> supplier) {
        long started = System.nanoTime();
        return new TimedResults(supplier.get(), elapsedMs(started));
    }

    private record TimedResults(List<RetrievalChunk> results, long elapsedMs) {
    }
}
