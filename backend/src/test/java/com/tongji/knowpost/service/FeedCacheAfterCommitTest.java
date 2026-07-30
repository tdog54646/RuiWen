package com.tongji.knowpost.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.cache.RedisKeyScanner;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FeedCacheAfterCommitTest {

    @Test
    void invalidationRunsOnlyAfterSuccessfulCommit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisKeyScanner scanner = mock(RedisKeyScanner.class);
        when(scanner.scan(anyString())).thenReturn(Set.of());
        Cache<String, FeedPageResponse> publicCache = Caffeine.newBuilder().build();
        Cache<String, FeedPageResponse> mineCache = Caffeine.newBuilder().build();
        FeedPageResponse page = new FeedPageResponse(List.of(), 1, 20, false);
        publicCache.put("feed:public:20:1:v2", page);
        mineCache.put("feed:mine:7:all:20:1:v2", page);
        FeedCacheService service = new FeedCacheService(redis, scanner, publicCache, mineCache);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.invalidatePostAfterCommit(7L, 12L);
            assertNotNull(publicCache.getIfPresent("feed:public:20:1:v2"));
            assertNotNull(mineCache.getIfPresent("feed:mine:7:all:20:1:v2"));
            verifyNoInteractions(scanner);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
            assertNull(publicCache.getIfPresent("feed:public:20:1:v2"));
            assertNull(mineCache.getIfPresent("feed:mine:7:all:20:1:v2"));
            verify(scanner).scan("feed:public:*");
            verify(scanner).scan("feed:mine:7:*");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
