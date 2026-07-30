package com.tongji.cache;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 使用增量 SCAN 遍历 Redis 键，避免在线请求或定时任务执行阻塞式 KEYS。
 */
@Component
public class RedisKeyScanner {

    private static final long DEFAULT_COUNT = 500L;
    private final StringRedisTemplate redis;

    public RedisKeyScanner(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Set<String> scan(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(DEFAULT_COUNT)
                .build();
        Set<String> keys = redis.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> found = new LinkedHashSet<>();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    found.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return found;
        });
        return keys == null ? Set.of() : keys;
    }
}
