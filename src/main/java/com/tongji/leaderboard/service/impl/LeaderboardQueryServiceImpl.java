package com.tongji.leaderboard.service.impl;

import com.tongji.leaderboard.api.dto.RankPosition;
import com.tongji.leaderboard.api.dto.RankType;
import com.tongji.leaderboard.api.dto.TopListResult;
import com.tongji.leaderboard.api.error.LeaderboardErrorCode;
import com.tongji.leaderboard.api.error.LeaderboardException;
import com.tongji.leaderboard.config.LeaderboardProperties;
import com.tongji.leaderboard.schema.LeaderboardKeys;
import com.tongji.leaderboard.schema.LeaderboardRankName;
import com.tongji.leaderboard.service.LeaderboardQueryService;
import com.tongji.leaderboard.service.SegmentTreeService;
import com.tongji.leaderboard.service.TopLeaderboardService;
import com.tongji.user.domain.User;
import com.tongji.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 排行榜查询服务实现：单查三段式、批量查询、Top 查询。
 * 对作者榜而言，接口中的 userId 语义为作者ID（ownerId）。
 */
@Service
@RequiredArgsConstructor
public class LeaderboardQueryServiceImpl implements LeaderboardQueryService {

    /** 用户计数字段：今日累计分。 */
    private static final String USER_COUNT_FIELD_TODAY = "today";

    private final TopLeaderboardService topLeaderboardService;
    private final SegmentTreeService segmentTreeService;
    private final StringRedisTemplate redisTemplate;
    private final LeaderboardProperties properties;
    private final UserService userService;

    /**
     * 单个用户查询：先精确名次，未命中再估算（userId 语义为作者ID）。
     */
    @Override
    public RankPosition getUserPosition(String leaderboardType, String date, long userId) {
        String rankName = buildRankNameOrThrow(leaderboardType, date);
        long score = getRealtimeScore(rankName, userId);
        UserProfile userProfile = resolveUserProfile(userId);
        Optional<Long> exactRank = topLeaderboardService.getExactRank(rankName, userId);
        if (exactRank.isPresent()) {
            return new RankPosition(
                    leaderboardType, date, userId, score, exactRank.get(), RankType.EXACT,
                    userProfile.nickname(), userProfile.avatar());
        }
        if (score <= 0) {
            return new RankPosition(
                    leaderboardType, date, userId, 0, 0, RankType.UNRANKED,
                    userProfile.nickname(), userProfile.avatar());
        }
        long estimate = segmentTreeService.estimateRank(rankName, score);
        return new RankPosition(
                leaderboardType, date, userId, score, estimate, RankType.ESTIMATE,
                userProfile.nickname(), userProfile.avatar());
    }

    /**
     * 批量查询：先 pipeline 查精确名次，再补实时分与估算名次（userId 语义为作者ID）。
     */
    @Override
    public List<RankPosition> batchGetUserPosition(String leaderboardType, String date, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (userIds.size() > properties.getBatchLimit()) {
            throw new LeaderboardException(LeaderboardErrorCode.BATCH_LIMIT_EXCEEDED);
        }
        String rankName = buildRankNameOrThrow(leaderboardType, date);
        Map<Long, Long> exactRankMap = batchGetExactRanks(rankName, userIds);
        Map<Long, Long> scoreMap = batchGetRealtimeScores(rankName, userIds);
        Map<Long, UserProfile> userProfileMap = resolveUserProfileMap(userIds);

        List<RankPosition> out = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            long score = scoreMap.getOrDefault(userId, 0L);
            Long exactRank = exactRankMap.get(userId);
            UserProfile userProfile = userProfileMap.getOrDefault(userId, UserProfile.EMPTY);
            if (exactRank != null) {
                out.add(new RankPosition(
                        leaderboardType, date, userId, score, exactRank, RankType.EXACT,
                        userProfile.nickname(), userProfile.avatar()));
                continue;
            }
            if (score <= 0) {
                out.add(new RankPosition(
                        leaderboardType, date, userId, 0, 0, RankType.UNRANKED,
                        userProfile.nickname(), userProfile.avatar()));
                continue;
            }
            long estimate = segmentTreeService.estimateRank(rankName, score);
            out.add(new RankPosition(
                    leaderboardType, date, userId, score, estimate, RankType.ESTIMATE,
                    userProfile.nickname(), userProfile.avatar()));
        }
        return out;
    }

    /**
     * 查询 Top 榜分页列表。
     */
    @Override
    public TopListResult listTop(String leaderboardType, String date, int offset, int limit) {
        if (offset < 0 || limit <= 0 || limit > properties.getTopQueryLimitMax()) {
            throw new LeaderboardException(LeaderboardErrorCode.BAD_REQUEST, "offset/limit 参数非法");
        }
        String rankName = buildRankNameOrThrow(leaderboardType, date);
        List<com.tongji.leaderboard.api.dto.RankItem> items = topLeaderboardService.listTop(rankName, offset, limit);
        Map<Long, UserProfile> userProfileMap = resolveUserProfileMap(items.stream()
                .map(com.tongji.leaderboard.api.dto.RankItem::userId)
                .toList());
        List<com.tongji.leaderboard.api.dto.RankItem> enrichedItems = new ArrayList<>(items.size());
        for (com.tongji.leaderboard.api.dto.RankItem item : items) {
            UserProfile userProfile = userProfileMap.getOrDefault(item.userId(), UserProfile.EMPTY);
            enrichedItems.add(new com.tongji.leaderboard.api.dto.RankItem(
                    item.rank(),
                    item.userId(),
                    item.score(),
                    userProfile.nickname(),
                    userProfile.avatar()));
        }
        boolean hasMore = items.size() == limit;
        return new TopListResult(leaderboardType, date, offset, limit, enrichedItems, hasMore);
    }

    /**
     * 组装 rankName 并将非法参数转业务异常。
     */
    private String buildRankNameOrThrow(String leaderboardType, String date) {
        try {
            return LeaderboardRankName.build(leaderboardType, date);
        } catch (IllegalArgumentException ex) {
            throw new LeaderboardException(LeaderboardErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    /**
     * 读取实时分：today + 未刷写 agg 增量（按 rankName + ownerId 对应键读取）。
     */
    private long getRealtimeScore(String rankName, long userId) {
        try {
            String countKey = LeaderboardKeys.userCountKey(rankName, userId);
            String aggKey = LeaderboardKeys.aggKey(rankName, userId);
            long today = parseLong(redisTemplate.opsForHash().get(countKey, USER_COUNT_FIELD_TODAY));
            Map<Object, Object> agg = redisTemplate.opsForHash().entries(aggKey);
            long delta = 0L;
            for (Object value : agg.values()) {
                delta += parseLong(value);
            }
            return Math.max(0, today + delta);
        } catch (Exception ex) {
            throw new LeaderboardException(LeaderboardErrorCode.COUNTER_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 批量读取精确名次（ZREVRANK pipeline）。
     */
    private Map<Long, Long> batchGetExactRanks(String rankName, List<Long> userIds) {
        String key = LeaderboardKeys.zsetKey(rankName);
        List<Object> pipelineResult = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            for (Long userId : userIds) {
                connection.zSetCommands().zRevRank(keyBytes, String.valueOf(userId).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        Map<Long, Long> rankMap = new HashMap<>(userIds.size());
        for (int i = 0; i < userIds.size(); i++) {
            Object raw = i < pipelineResult.size() ? pipelineResult.get(i) : null;
            if (raw instanceof Number n) {
                rankMap.put(userIds.get(i), n.longValue() + 1);
            }
        }
        return rankMap;
    }

    /**
     * 批量读取实时分（HGET today + HVALS agg pipeline）。
     */
    private Map<Long, Long> batchGetRealtimeScores(String rankName, List<Long> userIds) {
        List<Object> pipelineResult = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long userId : userIds) {
                String countKey = LeaderboardKeys.userCountKey(rankName, userId);
                connection.hashCommands().hGet(
                        countKey.getBytes(StandardCharsets.UTF_8),
                        USER_COUNT_FIELD_TODAY.getBytes(StandardCharsets.UTF_8));
                String aggKey = LeaderboardKeys.aggKey(rankName, userId);
                connection.hashCommands().hVals(aggKey.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        Map<Long, Long> scoreMap = new HashMap<>(userIds.size());
        for (int i = 0; i < userIds.size(); i++) {
            int base = i * 2;
            Object todayObj = base < pipelineResult.size() ? pipelineResult.get(base) : null;
            Object aggObj = (base + 1) < pipelineResult.size() ? pipelineResult.get(base + 1) : null;

            long today = parseLong(todayObj);
            long delta = 0L;
            if (aggObj instanceof List<?> values) {
                for (Object value : values) {
                    delta += parseLong(value);
                }
            }
            scoreMap.put(userIds.get(i), Math.max(0, today + delta));
        }
        return scoreMap;
    }

    /**
     * 容错转换 long，无法转换时按 0 处理。
     */
    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length == 0) {
                return 0L;
            }
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private UserProfile resolveUserProfile(long userId) {
        return userService.findById(userId)
                .map(this::toUserProfile)
                .orElse(UserProfile.EMPTY);
    }

    private Map<Long, UserProfile> resolveUserProfileMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProfile> userProfileMap = new HashMap<>(userIds.size());
        for (Long userId : userIds) {
            if (userId == null || userProfileMap.containsKey(userId)) {
                continue;
            }
            userProfileMap.put(userId, resolveUserProfile(userId));
        }
        return userProfileMap;
    }

    private UserProfile toUserProfile(User user) {
        return new UserProfile(user.getNickname(), user.getAvatar());
    }

    private record UserProfile(String nickname, String avatar) {
        private static final UserProfile EMPTY = new UserProfile(null, null);
    }
}
