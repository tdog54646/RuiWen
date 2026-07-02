package com.tongji.leaderboard.service;

import com.tongji.leaderboard.api.dto.RankPosition;
import com.tongji.leaderboard.api.dto.TopListResult;

import java.util.List;

/**
 * 排行榜查询聚合服务。
 */
public interface LeaderboardQueryService {

    /**
     * 单个用户查询当前名次（支持三段式）。
     */
    RankPosition getUserPosition(String leaderboardType, String date, long userId);

    /**
     * 批量查询用户当前名次。
     */
    List<RankPosition> batchGetUserPosition(String leaderboardType, String date, List<Long> userIds);

    /**
     * 查询 Top 榜列表。
     */
    TopListResult listTop(String leaderboardType, String date, int offset, int limit);
}
