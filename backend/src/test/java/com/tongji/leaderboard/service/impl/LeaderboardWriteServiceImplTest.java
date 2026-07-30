package com.tongji.leaderboard.service.impl;

import com.tongji.cache.RedisKeyScanner;
import com.tongji.leaderboard.config.LeaderboardProperties;
import com.tongji.leaderboard.schema.LeaderboardKeys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaderboardWriteServiceImplTest {

    @Test
    void eventDedupAndAggregateDrainAreSingleRedisScripts() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisKeyScanner scanner = mock(RedisKeyScanner.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        LeaderboardProperties properties = new LeaderboardProperties();
        LeaderboardWriteServiceImpl service = new LeaderboardWriteServiceImpl(redis, scanner, properties);
        String rank = "like:daily:20260730";

        service.onCounterEvent("evt-1", 7L, rank, 1L);
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of(
                        LeaderboardKeys.aggKey(rank, 7L), LeaderboardKeys.dedupKey("evt-1"))),
                any(Object[].class));

        clearInvocations(redis);
        when(scanner.scan("lb:agg:*")).thenReturn(Set.of(LeaderboardKeys.aggKey(rank, 7L)));
        service.flushAggToLeaderboard();

        @SuppressWarnings("rawtypes") ArgumentCaptor<DefaultRedisScript> script =
                ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redis).execute(script.capture(), argThat(keys -> keys.size() == 4), any(Object[].class));
        String source = script.getValue().getScriptAsString();
        assertTrue(source.contains("redis.call('DEL', KEYS[1])"));
        assertTrue(source.contains("redis.call('ZADD', KEYS[3]"));
        assertTrue(source.contains("redis.call('HSET', KEYS[2]"));
    }
}
