package com.tongji.leaderboard.service.impl;

import com.tongji.leaderboard.api.error.LeaderboardErrorCode;
import com.tongji.leaderboard.api.error.LeaderboardException;
import com.tongji.leaderboard.config.LeaderboardProperties;
import com.tongji.leaderboard.schema.LeaderboardKeys;
import com.tongji.leaderboard.service.LeaderboardWriteService;
import com.tongji.leaderboard.service.SegmentTreeService;
import com.tongji.leaderboard.service.TopLeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 排行榜写链路实现：幂等去重 + Top 更新 + 线段树更新（按作者 ownerId 入榜）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardWriteServiceImpl implements LeaderboardWriteService {

    private static final String USER_COUNT_FIELD_TODAY = "today";
    private static final String AGG_KEY_PREFIX = "lb:agg:";

    private final StringRedisTemplate redisTemplate;
    private final TopLeaderboardService topLeaderboardService;
    private final SegmentTreeService segmentTreeService;
    private final LeaderboardProperties properties;

    /**
     * 消费分数变更并更新排行榜存储。
     */
    @Override
    public void onCounterEvent(String eventId, long ownerId, String rankName, long delta) {
        if (eventId == null || eventId.isBlank() || rankName == null || rankName.isBlank()) {
            throw new LeaderboardException(LeaderboardErrorCode.BAD_REQUEST, "事件参数非法");
        }
        try {
            String aggKey = LeaderboardKeys.aggKey(rankName, ownerId);
            String aggField = eventId;
            redisTemplate.opsForHash().increment(aggKey, aggField, delta);
        } catch (Exception ex) {
            throw new LeaderboardException(LeaderboardErrorCode.STORAGE_WRITE_FAILED);
        }
    }

    /**
     * 每秒将聚合桶刷写到用户计数并更新榜单结构。
     */
    @Scheduled(fixedDelay = 1000L)
    public void flushAggToLeaderboard() {
        Set<String> aggKeys = redisTemplate.keys(AGG_KEY_PREFIX + "*");
        if (aggKeys == null || aggKeys.isEmpty()) {
            return;
        }

        for (String aggKey : aggKeys) {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(aggKey);
            if (entries.isEmpty()) {
                continue;
            }
            AggKeyInfo keyInfo = parseAggKey(aggKey);
            if (keyInfo == null) {
                continue;
            }
            long deltaSum = 0L;
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                long delta;
                try {
                    delta = Long.parseLong(String.valueOf(entry.getValue()));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (delta == 0) {
                    continue;
                }
                deltaSum += delta;
            }

            if (deltaSum == 0) {
                redisTemplate.delete(aggKey);
                continue;
            }

            String userCountKey = LeaderboardKeys.userCountKey(keyInfo.rankName(), keyInfo.ownerId());
            try {
                Long newScoreVal = redisTemplate.opsForHash().increment(userCountKey, USER_COUNT_FIELD_TODAY, deltaSum);
                long newScore = newScoreVal == null ? 0L : newScoreVal;
                long oldScore = newScore - deltaSum;
                topLeaderboardService.upsertScore(keyInfo.rankName(), keyInfo.ownerId(), newScore);
                segmentTreeService.updatePath(keyInfo.rankName(), oldScore, newScore);
                redisTemplate.delete(aggKey);
            } catch (Exception ex) {
                // 回滚用户计数，保留聚合桶以便后续重试。
                redisTemplate.opsForHash().increment(userCountKey, USER_COUNT_FIELD_TODAY, -deltaSum);
                log.warn("Flush leaderboard agg failed, aggKey={}, rankName={}, ownerId={}",
                        aggKey, keyInfo.rankName(), keyInfo.ownerId(), ex);
            }
        }
    }

    private AggKeyInfo parseAggKey(String aggKey) {
        if (aggKey == null || !aggKey.startsWith(AGG_KEY_PREFIX)) {
            return null;
        }
        int splitPos = aggKey.lastIndexOf(':');
        if (splitPos <= AGG_KEY_PREFIX.length()) {
            return null;
        }
        String rankName = aggKey.substring(AGG_KEY_PREFIX.length(), splitPos);
        if (rankName.isBlank()) {
            return null;
        }
        String ownerIdPart = aggKey.substring(splitPos + 1);
        try {
            long ownerId = Long.parseLong(ownerIdPart);
            return new AggKeyInfo(rankName, ownerId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record AggKeyInfo(String rankName, long ownerId) {
    }
}
