package com.tongji.leaderboard.service.impl;

import com.tongji.leaderboard.api.dto.RankItem;
import com.tongji.leaderboard.config.LeaderboardProperties;
import com.tongji.leaderboard.schema.LeaderboardKeys;
import com.tongji.leaderboard.service.TopLeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Top 榜 Redis 实现：ZADD/ZREVRANK/ZREVRANGE。
 */
@Service
@RequiredArgsConstructor
public class TopLeaderboardServiceImpl implements TopLeaderboardService {

    private final StringRedisTemplate redisTemplate;
    private final LeaderboardProperties properties;

    /**
     * 写入用户分数并执行 TopN 裁剪。
     */
    @Override
    public void upsertScore(String rankName, long userId, long score) {
        String key = LeaderboardKeys.zsetKey(rankName);
        redisTemplate.opsForZSet().add(key, String.valueOf(userId), score);
        // 仅保留头部 TopN，低分尾部直接裁剪，避免 ZSET 膨胀。
        redisTemplate.opsForZSet().removeRange(key, 0, -properties.getTopNMaxSize() - 1L);
    }

    /**
     * 查询用户在 Top 榜中的精确名次。
     */
    @Override
    public Optional<Long> getExactRank(String rankName, long userId) {
        String key = LeaderboardKeys.zsetKey(rankName);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(userId));
        return rank == null ? Optional.empty() : Optional.of(rank + 1);
    }

    /**
     * 分页查询 Top 榜条目。
     */
    @Override
    public List<RankItem> listTop(String rankName, int offset, int limit) {
        String key = LeaderboardKeys.zsetKey(rankName);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, offset, (long) offset + limit - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<RankItem> items = new ArrayList<>(tuples.size());
        int index = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null || tuple.getScore() == null) {
                continue;
            }
            items.add(new RankItem(
                    offset + index + 1L,
                    Long.parseLong(tuple.getValue()),
                    tuple.getScore().longValue(),
                    null,
                    null));
            index++;
        }
        return items;
    }
}
