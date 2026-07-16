package com.tongji.admin.dto;

import java.util.List;

/** RAG 向量索引统计。 */
public record IndexStatsResponse(
        long totalChunks,
        long distinctPosts,
        List<VisibleBucket> byVisible
) {
    /** 单个可见性档位的切片数。 */
    public record VisibleBucket(String visible, long count) {}
}
