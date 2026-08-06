package com.tongji.llm.rag.search;

import com.tongji.llm.rag.model.RetrievalChunk;

import java.util.List;

/**
 * 一次检索的分阶段快照。快照在各阶段完成时复制分数，避免 RRF/Reranker 后续修改
 * {@link RetrievalChunk} 时丢失原始向量分或 BM25 分。
 */
public record RetrievalTrace(
        String query,
        String index,
        RetrievalOptions options,
        List<RankedChunk> vector,
        List<RankedChunk> bm25,
        List<RankedChunk> fused,
        List<RankedChunk> reranked,
        List<RankedChunk> selected,
        Latency latency,
        boolean rerankerApplied,
        String fallbackReason) {

    public RetrievalTrace {
        vector = List.copyOf(vector);
        bm25 = List.copyOf(bm25);
        fused = List.copyOf(fused);
        reranked = List.copyOf(reranked);
        selected = List.copyOf(selected);
    }

    public static List<RankedChunk> snapshot(List<RetrievalChunk> chunks, Score score) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        java.util.ArrayList<RankedChunk> result = new java.util.ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalChunk c = chunks.get(i);
            double value = score == Score.RERANK ? c.getRerankScore() : c.getRrfScore();
            result.add(new RankedChunk(
                    c.getChunkId(), c.getPostId(), c.getTitle(), c.getPosition(),
                    i + 1, value, c.getContent()));
        }
        return result;
    }

    public enum Score { SOURCE, RRF, RERANK }

    public record RankedChunk(
            String chunkId,
            String postId,
            String title,
            int position,
            int rank,
            double score,
            String content) {
    }

    public record Latency(
            long embeddingMs,
            long vectorMs,
            long bm25Ms,
            long fusionMs,
            long rerankMs,
            long totalMs) {
    }
}
