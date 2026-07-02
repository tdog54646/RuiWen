package com.tongji.llm.rag.model;


import com.tongji.llm.rag.search.HybridSearchService;
import com.tongji.llm.rag.search.RrfFusionService;
import lombok.Data;

/**
 * 从 ES 检索返回的单个 Chunk（分块）数据传输对象。
 * <p>
 * 包含 Chunk 的业务标识信息、文本内容以及经过 RRF 融合后的综合得分。
 * 用于在向量检索路和 BM25 检索路之间传递统一的数据结构。
 *
 * @see RrfFusionService
 * @see HybridSearchService
 */
@Data
public class RetrievalChunk {

    /**
     * Chunk 全局唯一标识，格式为 "{postId}#{position}"，例如 "12345#3"。
     */
    private String chunkId;

    /**
     * 所属帖子的 ID。
     */
    private String postId;

    /**
     * Chunk 的原始文本内容，用于拼入 LLM Prompt 作为上下文。
     */
    private String content;

    /**
     * 所属帖子的标题，用于在 Prompt 中标注上下文来源。
     */
    private String title;

    /**
     * 该 Chunk 在原文章中的位置序号（从 0 开始），用于排序和去重展示。
     */
    private int position;

    /**
     * RRF 融合后的综合得分。值越大表示综合相关性越高。
     * <p>
     * 计算公式：RRF_Score = Σ(1.0 / (K + rank_i))，其中 K=60，rank_i 为该 Chunk 在各路检索中的排名。
     * 如果仅一路召回，则只有一路得分；两路都命中则得分相加。
     */
    private double rrfScore;

    public RetrievalChunk() {}

    public RetrievalChunk(String chunkId, String postId, String content, String title, int position, double rrfScore) {
        this.chunkId = chunkId;
        this.postId = postId;
        this.content = content;
        this.title = title;
        this.position = position;
        this.rrfScore = rrfScore;
    }

    /**
     * 快速构造方法，仅保留最核心字段。
     */
    public static RetrievalChunk of(String chunkId, String postId, String content, String title, int position) {
        return new RetrievalChunk(chunkId, postId, content, title, position, 0.0);
    }
}
