package com.tongji.leaderboard.service.impl;

import com.tongji.leaderboard.config.LeaderboardProperties;
import com.tongji.leaderboard.schema.LeaderboardKeys;
import com.tongji.leaderboard.service.SegmentTreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线段树服务 Redis 实现：路径更新 + 粗估名次。
 */
@Service
@RequiredArgsConstructor
public class SegmentTreeServiceImpl implements SegmentTreeService {

    private final StringRedisTemplate redisTemplate;
    private final LeaderboardProperties properties;

    /** 路径批量更新脚本：一次性提交多个 HINCRBY。 */
    private static final String UPDATE_PATH_LUA = """
            local hashKey = KEYS[1]
            for i = 1, #ARGV, 2 do
              redis.call('HINCRBY', hashKey, ARGV[i], tonumber(ARGV[i + 1]))
            end
            return 1
            """;

    /**
     * 根据 old/new 分数更新线段树路径计数。
     */
    @Override
    public void updatePath(String rankName, long oldScore, long newScore) {
        Map<String, Long> deltaMap = new LinkedHashMap<>();
        addPathDelta(deltaMap, oldScore, -1);
        addPathDelta(deltaMap, newScore, +1);
        if (deltaMap.isEmpty()) {
            return;
        }

        List<String> args = new ArrayList<>(deltaMap.size() * 2);
        for (Map.Entry<String, Long> entry : deltaMap.entrySet()) {
            long delta = entry.getValue();
            if (delta == 0L) {
                continue;
            }
            args.add(entry.getKey());
            args.add(String.valueOf(delta));
        }
        if (args.isEmpty()) {
            return;
        }

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(UPDATE_PATH_LUA);
        redisTemplate.execute(script, List.of(LeaderboardKeys.segmentKey(rankName)), args.toArray());
    }

    /**
     * 基于区间统计做 O(logN) 粗估名次。
     */
    @Override
    public long estimateRank(String rankName, long score) {
        if (score <= 0) {
            return 0;
        }
        long normalizedScore = normalizeScore(score);
        long min = properties.getSegmentMinScore();
        long max = properties.getSegmentMaxScore();
        long bucket = normalizeBucketSize();

        long lower = min;
        long upper = max;
        List<String> higherBuckets = new ArrayList<>();
        while ((upper - lower + 1) > bucket) {
            long mid = lower + ((upper - lower) >>> 1);
            if (normalizedScore <= mid) {
                higherBuckets.add(rangeField(mid + 1, upper));
                upper = mid;
            } else {
                lower = mid + 1;
            }
        }

        String segKey = LeaderboardKeys.segmentKey(rankName);
        List<Object> fields = new ArrayList<>(higherBuckets.size() + 1);
        fields.addAll(higherBuckets);
        fields.add(rangeField(lower, upper));
        List<Object> values = redisTemplate.opsForHash().multiGet(segKey, fields);

        long biggerThanMe = 0L;
        for (int i = 0; i < higherBuckets.size(); i++) {
            biggerThanMe += parseLong(values != null && i < values.size() ? values.get(i) : null);
        }
        long leafCount = parseLong(values != null && !values.isEmpty() ? values.get(values.size() - 1) : null);

        long segmentSpan = upper - lower + 1;
        double ratio = (double) (upper - normalizedScore) / segmentSpan;
        long segmentRank = Math.max(0L, Math.round(ratio * leafCount));
        return Math.max(1L, biggerThanMe + segmentRank + 1L);
    }

    /**
     * 将单个分数映射到线段树路径并累加增量。
     */
    private void addPathDelta(Map<String, Long> deltaMap, long score, int delta) {
        if (score <= 0) {
            return;
        }
        long normalizedScore = normalizeScore(score);
        long lower = properties.getSegmentMinScore();
        long upper = properties.getSegmentMaxScore();
        long bucket = normalizeBucketSize();

        while ((upper - lower + 1) > bucket) {
            String field = rangeField(lower, upper);
            deltaMap.merge(field, (long) delta, Long::sum);
            long mid = lower + ((upper - lower) >>> 1);
            if (normalizedScore <= mid) {
                upper = mid;
            } else {
                lower = mid + 1;
            }
        }
        deltaMap.merge(rangeField(lower, upper), (long) delta, Long::sum);
    }

    /**
     * 将分数裁剪到配置区间。
     */
    private long normalizeScore(long score) {
        return Math.min(Math.max(score, properties.getSegmentMinScore()), properties.getSegmentMaxScore());
    }

    /**
     * 归一化叶子区间宽度。
     */
    private long normalizeBucketSize() {
        return Math.max(1, properties.getSegmentBucketSize());
    }

    /**
     * 区间字段编码：lower-upper。
     */
    private String rangeField(long lower, long upper) {
        return lower + "-" + upper;
    }

    /**
     * 容错转换 long。
     */
    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
