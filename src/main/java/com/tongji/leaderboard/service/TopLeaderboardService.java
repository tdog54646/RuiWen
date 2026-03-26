package com.tongji.leaderboard.service;

import com.tongji.leaderboard.api.dto.RankItem;

import java.util.List;
import java.util.Optional;

/**
 * Top 榜精确读写服务。
 */
public interface TopLeaderboardService {

    /**
     * 写入或更新用户分数。
     */
    void upsertScore(String rankName, long userId, long score);

    /**
     * 查询用户精确名次（TopN 内）。
     */
    Optional<Long> getExactRank(String rankName, long userId);

    /**
     * 分页查询榜单头部列表。
     */
    List<RankItem> listTop(String rankName, int offset, int limit);
}
