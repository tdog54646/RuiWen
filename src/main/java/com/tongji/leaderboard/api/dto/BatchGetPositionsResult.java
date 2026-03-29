package com.tongji.leaderboard.api.dto;

import java.util.List;

/**
 * 批量查询用户名次响应体。
 */
public record BatchGetPositionsResult(
        String leaderboardType,
        String date,
        List<RankPosition> items) {
}
