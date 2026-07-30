package com.tongji.knowpost.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.cache.RedisKeyScanner;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/** 在数据库事务提交后统一失效详情、首页和个人 Feed 缓存。 */
@Service
@Slf4j
public class FeedCacheService {

    public static final String PUBLIC_FEED_CACHE_INVALIDATE_CHANNEL = "knowpost:feed:public:invalidate";

    private final StringRedisTemplate redis;
    private final RedisKeyScanner keyScanner;
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final Cache<String, FeedPageResponse> feedMineCache;

    public FeedCacheService(StringRedisTemplate redis,
                            RedisKeyScanner keyScanner,
                            @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache,
                            @Qualifier("feedMineCache") Cache<String, FeedPageResponse> feedMineCache) {
        this.redis = redis;
        this.keyScanner = keyScanner;
        this.feedPublicCache = feedPublicCache;
        this.feedMineCache = feedMineCache;
    }

    /**
     * 事务内调用时只注册 afterCommit 回调，避免提交前删除后被并发请求重新填入旧数据。
     */
    public void invalidatePostAfterCommit(long creatorId, long postId) {
        Runnable invalidation = () -> {
            try {
                invalidatePostNow(creatorId, postId);
            } catch (Exception e) {
                log.error("Post cache invalidation failed after commit, creatorId={}, postId={}",
                        creatorId, postId, e);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
        } else {
            invalidation.run();
        }
    }

    public void invalidatePostNow(long creatorId, long postId) {
        deletePublicFeedCaches();
        deletePersonalFeedCaches(creatorId);
        try {
            redis.delete("knowpost:detail:" + postId + ":v2");
            redis.delete("feed:item:" + postId);
        } catch (Exception e) {
            log.warn("Redis detail cache invalidation failed, postId={}", postId, e);
        }
    }

    public void deletePublicFeedCaches() {
        feedPublicCache.invalidateAll();
        try {
            Set<String> keys = keyScanner.scan("feed:public:*");
            if (!keys.isEmpty()) redis.delete(keys);
            redis.convertAndSend(PUBLIC_FEED_CACHE_INVALIDATE_CHANNEL,
                    String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Redis public feed cache invalidation failed", e);
        }
    }

    /** 供 Redis 失效广播订阅者清理当前实例的 L1 缓存。 */
    public void deletePublicFeedLocalCaches() {
        feedPublicCache.invalidateAll();
    }

    public void deletePersonalFeedCaches(long userId) {
        String prefix = "feed:mine:" + userId + ":";
        feedMineCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        try {
            Set<String> keys = keyScanner.scan(prefix + "*");
            if (!keys.isEmpty()) redis.delete(keys);
        } catch (Exception e) {
            log.warn("Redis personal feed cache invalidation failed, userId={}", userId, e);
        }
    }
}
