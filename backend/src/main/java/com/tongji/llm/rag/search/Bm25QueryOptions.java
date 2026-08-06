package com.tongji.llm.rag.search;

/** BM25 查询构造参数；生产检索与离线调优共用，避免评测实现漂移。 */
public record Bm25QueryOptions(
        double titleBoost,
        double phraseBoost,
        int phraseSlop,
        String minimumShouldMatch,
        String queryType,
        String fuzziness) {

    public Bm25QueryOptions {
        if (titleBoost < 0) throw new IllegalArgumentException("titleBoost 不能小于 0");
        if (phraseBoost < 0) throw new IllegalArgumentException("phraseBoost 不能小于 0");
        if (phraseSlop < 0) throw new IllegalArgumentException("phraseSlop 不能小于 0");
        minimumShouldMatch = minimumShouldMatch == null || minimumShouldMatch.isBlank()
                ? "1" : minimumShouldMatch.trim();
        queryType = queryType == null || queryType.isBlank()
                ? "best_fields" : queryType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("best_fields", "most_fields", "cross_fields").contains(queryType)) {
            throw new IllegalArgumentException("不支持的 BM25 queryType: " + queryType);
        }
        fuzziness = fuzziness == null || fuzziness.isBlank()
                ? "NONE" : fuzziness.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
