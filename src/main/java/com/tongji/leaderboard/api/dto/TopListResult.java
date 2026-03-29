package com.tongji.leaderboard.api.dto;

import java.util.List;

/**
 * Top 榜查询结果。
 */
public record TopListResult(
        String leaderboardType,
        String date,
        int offset,
        int limit,
        List<RankItem> items,
        boolean hasMore) {
}
