package com.tongji.leaderboard.api.dto;

/**
 * 榜单条目：名次、用户、分数。
 */
public record RankItem(
        long rank,
        long userId,
        long score,
        String nickname,
        String avatar) {
}
