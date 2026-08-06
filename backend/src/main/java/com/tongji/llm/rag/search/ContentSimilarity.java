package com.tongji.llm.rag.search;

import java.util.LinkedHashSet;
import java.util.Set;

/** 用轻量字符 5-gram 识别模板化文章和最终上下文中的近重复内容。 */
public final class ContentSimilarity {

    public static final double DEFAULT_THRESHOLD = 0.45;
    private static final int SHINGLE_SIZE = 5;

    private ContentSimilarity() {
    }

    public static boolean isNearDuplicate(String left, String right, double threshold) {
        return jaccard(left, right) >= threshold;
    }

    public static double jaccard(String left, String right) {
        Set<String> leftShingles = shingles(left);
        Set<String> rightShingles = shingles(right);
        if (leftShingles.isEmpty() || rightShingles.isEmpty()) return 0;
        Set<String> smaller = leftShingles.size() <= rightShingles.size()
                ? leftShingles : rightShingles;
        Set<String> larger = smaller == leftShingles ? rightShingles : leftShingles;
        int intersection = 0;
        for (String shingle : smaller) {
            if (larger.contains(shingle)) intersection++;
        }
        int union = leftShingles.size() + rightShingles.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    static Set<String> shingles(String value) {
        String normalized = value == null ? "" : value
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^0-9a-z\\p{IsHan}]+", "");
        if (normalized.isEmpty()) return Set.of();
        if (normalized.length() <= SHINGLE_SIZE) return Set.of(normalized);
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i <= normalized.length() - SHINGLE_SIZE; i++) {
            result.add(normalized.substring(i, i + SHINGLE_SIZE));
        }
        return Set.copyOf(result);
    }
}
