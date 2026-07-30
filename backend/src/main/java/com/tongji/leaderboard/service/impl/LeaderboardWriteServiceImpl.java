package com.tongji.leaderboard.service.impl;

import com.tongji.cache.RedisKeyScanner;
import com.tongji.leaderboard.api.error.LeaderboardErrorCode;
import com.tongji.leaderboard.api.error.LeaderboardException;
import com.tongji.leaderboard.config.LeaderboardProperties;
import com.tongji.leaderboard.schema.LeaderboardKeys;
import com.tongji.leaderboard.service.LeaderboardWriteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 排行榜写链路：事件幂等入桶，并原子完成聚合桶、用户分数、TopN 与线段树更新。 */
@Slf4j
@Service
public class LeaderboardWriteServiceImpl implements LeaderboardWriteService {

    private static final String USER_COUNT_FIELD_TODAY = "today";
    private static final String AGG_KEY_PREFIX = "lb:agg:";

    private final StringRedisTemplate redis;
    private final RedisKeyScanner keyScanner;
    private final LeaderboardProperties properties;
    private final DefaultRedisScript<Long> acceptEventScript;
    private final DefaultRedisScript<Long> drainScript;

    public LeaderboardWriteServiceImpl(StringRedisTemplate redis, RedisKeyScanner keyScanner,
                                       LeaderboardProperties properties) {
        this.redis = redis;
        this.keyScanner = keyScanner;
        this.properties = properties;
        this.acceptEventScript = script(ACCEPT_EVENT_LUA);
        this.drainScript = script(DRAIN_AGG_LUA);
    }

    @Override
    public void onCounterEvent(String eventId, long ownerId, String rankName, long delta) {
        if (eventId == null || eventId.isBlank() || rankName == null || rankName.isBlank()
                || ownerId <= 0 || (delta != 1 && delta != -1)) {
            throw new LeaderboardException(LeaderboardErrorCode.BAD_REQUEST, "事件参数非法");
        }
        try {
            Long result = redis.execute(acceptEventScript,
                    List.of(LeaderboardKeys.aggKey(rankName, ownerId), LeaderboardKeys.dedupKey(eventId)),
                    eventId, String.valueOf(delta),
                    String.valueOf(Math.max(60L, properties.getDedupTtl().toSeconds())));
            if (result != null && result < 0) {
                throw new IllegalStateException("同一排行榜事件出现不同增量");
            }
        } catch (Exception ex) {
            throw new LeaderboardException(LeaderboardErrorCode.STORAGE_WRITE_FAILED);
        }
    }

    @Scheduled(fixedDelay = 1000L)
    public void flushAggToLeaderboard() {
        Set<String> aggKeys = keyScanner.scan(AGG_KEY_PREFIX + "*");
        for (String aggKey : aggKeys) {
            AggKeyInfo info = parseAggKey(aggKey);
            if (info == null) {
                log.warn("Skip malformed leaderboard aggregate key: {}", aggKey);
                continue;
            }
            try {
                redis.execute(drainScript, List.of(
                                aggKey,
                                LeaderboardKeys.userCountKey(info.rankName(), info.ownerId()),
                                LeaderboardKeys.zsetKey(info.rankName()),
                                LeaderboardKeys.segmentKey(info.rankName())),
                        USER_COUNT_FIELD_TODAY,
                        String.valueOf(info.ownerId()),
                        String.valueOf(properties.getTopNMaxSize()),
                        String.valueOf(properties.getSegmentMinScore()),
                        String.valueOf(properties.getSegmentMaxScore()),
                        String.valueOf(properties.getSegmentBucketSize()));
            } catch (Exception ex) {
                log.warn("Flush leaderboard aggregate failed, key={}", aggKey, ex);
            }
        }
    }

    private AggKeyInfo parseAggKey(String aggKey) {
        if (aggKey == null || !aggKey.startsWith(AGG_KEY_PREFIX)) return null;
        int split = aggKey.lastIndexOf(':');
        if (split <= AGG_KEY_PREFIX.length()) return null;
        String rankName = aggKey.substring(AGG_KEY_PREFIX.length(), split);
        try {
            return new AggKeyInfo(rankName, Long.parseLong(aggKey.substring(split + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(source);
        return script;
    }

    private record AggKeyInfo(String rankName, long ownerId) {}

    private static final String ACCEPT_EVENT_LUA = """
            if redis.call('SETNX', KEYS[2], '1') == 0 then return 0 end
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            return 1
            """;

    private static final String DRAIN_AGG_LUA = """
            local values = redis.call('HVALS', KEYS[1])
            if #values == 0 then return 0 end
            local delta = 0
            for i=1,#values do delta = delta + tonumber(values[i]) end
            local oldScore = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
            local newScore = math.max(0, oldScore + delta)
            redis.call('HSET', KEYS[2], ARGV[1], newScore)
            redis.call('ZADD', KEYS[3], newScore, ARGV[2])
            local topN = math.max(1, tonumber(ARGV[3]))
            local excess = redis.call('ZCARD', KEYS[3]) - topN
            if excess > 0 then redis.call('ZREMRANGEBYRANK', KEYS[3], 0, excess - 1) end

            local minScore = tonumber(ARGV[4])
            local maxScore = tonumber(ARGV[5])
            local bucket = math.max(1, tonumber(ARGV[6]))
            local function updatePath(score, amount)
              if score <= 0 then return end
              local normalized = math.max(minScore, math.min(score, maxScore))
              local lower = minScore
              local upper = maxScore
              while (upper - lower + 1) > bucket do
                local field = tostring(lower) .. '-' .. tostring(upper)
                local value = redis.call('HINCRBY', KEYS[4], field, amount)
                if value == 0 then redis.call('HDEL', KEYS[4], field) end
                local mid = lower + math.floor((upper - lower) / 2)
                if normalized <= mid then upper = mid else lower = mid + 1 end
              end
              local field = tostring(lower) .. '-' .. tostring(upper)
              local value = redis.call('HINCRBY', KEYS[4], field, amount)
              if value == 0 then redis.call('HDEL', KEYS[4], field) end
            end
            if oldScore ~= newScore then
              updatePath(oldScore, -1)
              updatePath(newScore, 1)
            end
            redis.call('DEL', KEYS[1])
            return newScore
            """;
}
