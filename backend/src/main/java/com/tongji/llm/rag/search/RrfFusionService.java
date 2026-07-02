package com.tongji.llm.rag.search;

import com.tongji.llm.rag.model.RagProperties;
import com.tongji.llm.rag.model.RetrievalChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RRF（Reciprocal Rank Fusion，倒数排名融合）算法实现。
 * <p>
 * RRF 是一种无参数、无需训练的轻量级结果融合方法，
 * 被 RAGFlow、LightRAG、BM25S 等主流 RAG 框架广泛采用。
 *
 * <h2>算法原理</h2>
 * <p>
 * 对于同一个检索结果在多路检索中的排名，取其倒数求和作为最终得分：
 * <pre>
 * RRF_Score(d) = Σ  1.0 / (K + rank_i(d))
 * </pre>
 * 其中：
 * <ul>
 *   <li><b>K</b>：平滑常数（默认 60），作用是缓解排名差距对最终得分的影响。</li>
 *   <li><b>rank_i(d)</b>：文档 d 在第 i 路检索中的排名（从 1 开始）。</li>
 * </ul>
 *
 * <h2>为何选择 RRF？</h2>
 * <ul>
 *   <li><b>无超参数</b>：只需设置 K，无需训练或调参。</li>
 *   <li><b>排名鲁棒</b>：只依赖相对排名，不受各路得分量纲影响，向量检索的余弦相似度
 *       和 BM25 的原始得分不能直接比较，但排名可以。</li>
 *   <li><b>长尾友好</b>：K=60 使排名第 1（第 1 名得分 ≈ 1/61）和排名第 2（第 2 名得分 ≈ 1/62）
 *       的差距很小，避免一路独大。</li>
 *   <li><b>多路扩展</b>：可轻松扩展到三路、四路检索，只需在公式中增加 rank 项。</li>
 * </ul>
 *
 * <h2>本实现说明</h2>
 * <ul>
 *   <li>使用 {@link LinkedHashMap} 以 chunkId 为 key 合并两路结果，保证插入顺序。</li>
 *   <li>两路都命中的 Chunk 得分相加（代表跨路验证，更可信）。</li>
 *   <li>最终按 RRF_Score 降序排列返回。</li>
 * </ul>
 *
 * <h2>伪代码参考（RAGFlow）</h2>
 * <pre>
 * def rrf_fusion(vector_results, bm25_results, k=60):
 *     rrf_score_map = {}
 *     for rank, doc in enumerate(vector_results):
 *         doc_id = doc["id"]
 *         score = 1.0 / (k + rank + 1)   # rank 从 0 开始，故 +1
 *         rrf_score_map[doc_id] = {"doc": doc, "score": score}
 *     for rank, doc in enumerate(bm25_results):
 *         doc_id = doc["id"]
 *         score = 1.0 / (k + rank + 1)
 *         if doc_id in rrf_score_map:
 *             rrf_score_map[doc_id]["score"] += score
 *         else:
 *             rrf_score_map[doc_id] = {"doc": doc, "score": score}
 *     fused = list(rrf_score_map.values())
 *     fused.sort(key=lambda x: x["score"], reverse=True)
 *     return [item["doc"] for item in fused]
 * </pre>
 *
 * @see RetrievalChunk
 * @see HybridSearchService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RrfFusionService {

    private final RagProperties properties;

    /**
     * 对两路检索结果执行 RRF 融合。
     *
     * <p>处理流程：
     * <ol>
     *   <li>遍历向量检索结果，按排名计算 RRF 得分（1.0 / (K + rank)），
     *       存入以 chunkId 为 key 的 map（保留引用以防两路都命中）。</li>
     *   <li>遍历 BM25 检索结果，同样计算 RRF 得分：
     *       <ul>
     *         <li>若该 chunkId 已在 map 中（两路都命中），则将得分累加。</li>
     *         <li>若不存在（仅 BM25 命中），则新建 entry。</li>
     *       </ul>
     *   <li>将 map.values() 转为 List，按 rrfScore 降序排列。</li>
     *   <li>若设置了 minScore 阈值，低于该阈值的 Chunk 会被过滤掉。</li>
     * </ol>
     *
     * <p><b>示例：</b>
     * <pre>
     * List&lt;RetrievalChunk&gt; vectorResults = vectorSearch(query, topK=20);
     * List&lt;RetrievalChunk&gt; bm25Results = bm25Search(query, topK=20);
     * List&lt;RetrievalChunk&gt; fused = rrfFusionService.fuseResults(vectorResults, bm25Results);
     * // fused 已按综合 RRF 得分降序排列
     * </pre>
     *
     * @param vectorResults 向量检索召回结果列表，需按相似度从高到低排序（Rank 1 = 最高相似度）
     * @param bm25Results  BM25 关键词检索召回结果列表，需按 BM25 得分从高到低排序
     * @return 按 RRF 综合得分降序排列的 Chunk 列表。若两路都为空则返回空列表。
     */
    public List<RetrievalChunk> fuseResults(
            List<RetrievalChunk> vectorResults,
            List<RetrievalChunk> bm25Results
    ) {
        int k = properties.getRrf().getK();
        double minScore = properties.getRetrieval().getMinScore();

        // 使用 LinkedHashMap：key = chunkId，value = Accumulator（持有 chunk 引用和累计得分）
        // LinkedHashMap 保证按插入顺序遍历（先向量路，再 BM25 路）
        Map<String, Accumulator> scoreMap = new LinkedHashMap<>();

        // ----- 第一路：向量检索 RRF 得分 -----
        // 排名从 1 开始（Rank 1 = 相似度最高的文档）
        // 公式：1.0 / (k + rank)  =>  rank=1 时得分最高
        for (int rank = 0; rank < vectorResults.size(); rank++) {
            RetrievalChunk chunk = vectorResults.get(rank);
            double rrfScore = 1.0 / (k + rank + 1); // rank+1 因为 rank 从 0 开始
            scoreMap.put(chunk.getChunkId(), new Accumulator(chunk, rrfScore));
        }

        // ----- 第二路：BM25 检索 RRF 得分累加 -----
        for (int rank = 0; rank < bm25Results.size(); rank++) {
            RetrievalChunk chunk = bm25Results.get(rank);
            double rrfScore = 1.0 / (k + rank + 1);
            Accumulator acc = scoreMap.get(chunk.getChunkId());
            if (acc != null) {
                // 两路都命中：RRF 得分累加——代表跨路验证，结果更可信
                acc.addScore(rrfScore);
            } else {
                // 仅一路命中：新建 entry
                scoreMap.put(chunk.getChunkId(), new Accumulator(chunk, rrfScore));
            }
        }

        // ----- 按 RRF 累计得分降序排列 -----
        List<RetrievalChunk> fused = scoreMap.values().stream()
                .filter(acc -> acc.totalScore() >= minScore) // 按阈值过滤
                .sorted(Comparator.comparingDouble(Accumulator::totalScore).reversed())
                .map(Accumulator::chunk)
                .toList();

        log.debug("RRF fusion: vector={}, bm25={}, fused={}, top_score={}",
                vectorResults.size(), bm25Results.size(), fused.size(),
                fused.isEmpty() ? "N/A" : fused.getFirst().getRrfScore());

        return fused;
    }

    /**
     * 内部累加器：持有 chunk 引用和当前累计 RRF 得分。
     * <p>
     * 使用内部类而非在 RetrievalChunk 上直接累加 score，是为了：
     * <ul>
     *   <li>不污染 RetrievalChunk 的不可变语义。</li>
     *   <li>在遍历第二路时可以增量累加，而不必重建对象。</li>
     * </ul>
     */
    private static final class Accumulator {
        private final RetrievalChunk chunk;
        private double score;

        Accumulator(RetrievalChunk chunk, double initialScore) {
            this.chunk = chunk;
            this.score = initialScore;
            // 同步写入 chunk 的 rrfScore（用于最终排序和展示）
            chunk.setRrfScore(initialScore);
        }

        void addScore(double delta) {
            this.score += delta;
            this.chunk.setRrfScore(this.score);
        }

        double totalScore() {
            return this.score;
        }

        RetrievalChunk chunk() {
            return this.chunk;
        }
    }
}
