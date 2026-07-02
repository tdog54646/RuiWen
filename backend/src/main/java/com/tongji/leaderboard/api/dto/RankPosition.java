package com.tongji.leaderboard.api.dto;

/**
 * 用户名次结果：支持 EXACT/ESTIMATE/UNRANKED 三种语义。
 */
public record RankPosition(
        String leaderboardType,
        String date,
        long userId,
        long score,
        long rank,
        RankType rankType,
        String nickname,
        String avatar) {
}
