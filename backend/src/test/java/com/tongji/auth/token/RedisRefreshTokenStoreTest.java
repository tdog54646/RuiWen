package com.tongji.auth.token;

import com.tongji.cache.RedisKeyScanner;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RedisRefreshTokenStoreTest {

    @Test
    void consumeUsesAtomicGetAndDeleteAndOnlyAcceptsWhitelistedValue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete("auth:rt:7:token-a")).thenReturn("1");
        when(values.getAndDelete("auth:rt:7:token-b")).thenReturn(null);
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redis, mock(RedisKeyScanner.class));

        assertTrue(store.consumeToken(7L, "token-a"));
        assertFalse(store.consumeToken(7L, "token-b"));
        verify(values).getAndDelete("auth:rt:7:token-a");
        verify(values).getAndDelete("auth:rt:7:token-b");
    }
}
