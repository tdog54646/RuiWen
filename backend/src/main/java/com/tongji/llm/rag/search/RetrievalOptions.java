package com.tongji.llm.rag.search;

/** 一次检索运行的显式参数，用于生产默认值和离线网格评测共用同一实现。 */
public record RetrievalOptions(
        int retrievalTopK,
        int knnCandidates,
        int rrfK,
        int rerankTopK,
        int finalTopK,
        boolean rerankEnabled,
        double titleBoost,
        double phraseBoost,
        boolean diversityEnabled,
        double nearDuplicateThreshold) {

    public RetrievalOptions(
            int retrievalTopK,
            int knnCandidates,
            int rrfK,
            int rerankTopK,
            int finalTopK,
            boolean rerankEnabled) {
        this(retrievalTopK, knnCandidates, rrfK, rerankTopK, finalTopK, rerankEnabled,
                1.0, 0.0, true, ContentSimilarity.DEFAULT_THRESHOLD);
    }

    public RetrievalOptions {
        if (retrievalTopK <= 0) throw new IllegalArgumentException("retrievalTopK 必须大于 0");
        if (knnCandidates < retrievalTopK) throw new IllegalArgumentException("knnCandidates 不能小于 retrievalTopK");
        if (rrfK < 0) throw new IllegalArgumentException("rrfK 不能小于 0");
        if (rerankTopK <= 0) throw new IllegalArgumentException("rerankTopK 必须大于 0");
        if (finalTopK <= 0) throw new IllegalArgumentException("finalTopK 必须大于 0");
        if (!Double.isFinite(titleBoost) || titleBoost < 0) {
            throw new IllegalArgumentException("titleBoost 必须是非负有限数");
        }
        if (!Double.isFinite(phraseBoost) || phraseBoost < 0) {
            throw new IllegalArgumentException("phraseBoost 必须是非负有限数");
        }
        if (!Double.isFinite(nearDuplicateThreshold)
                || nearDuplicateThreshold <= 0 || nearDuplicateThreshold > 1) {
            throw new IllegalArgumentException("nearDuplicateThreshold 必须在 (0, 1] 范围内");
        }
    }
}
