package com.tongji.knowpost.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class FeedCacheService {

    public static final String PUBLIC_FEED_CACHE_INVALIDATE_CHANNEL = "knowpost:feed:public:invalidate";

    private final StringRedisTemplate redis;
    private final Cache<String, FeedPageResponse> feedPublicCache;

    public FeedCacheService(StringRedisTemplate redis,
                            @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache) {
        this.redis = redis;
        this.feedPublicCache = feedPublicCache;
    }

    public void deletePublicFeedRedisCaches() {
        Set<String> keys = redis.keys("feed:public:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public void deletePublicFeedLocalCaches() {
        feedPublicCache.invalidateAll();
    }

    public void publishDeletePublicFeedLocalCaches() {
        redis.convertAndSend(PUBLIC_FEED_CACHE_INVALIDATE_CHANNEL, String.valueOf(System.currentTimeMillis()));
    }

    public void deleteAndPublishPublicFeedCaches() {
        deletePublicFeedRedisCaches();
        deletePublicFeedLocalCaches();
        publishDeletePublicFeedLocalCaches();
    }

    public void doubleDeleteAndPublishPublicFeedCaches(long delayMillis) {
        deleteAndPublishPublicFeedCaches();
        try {
            Thread.sleep(Math.max(delayMillis, 50));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        deleteAndPublishPublicFeedCaches();
    }

    public void deleteAllFeedCaches() {
        deleteAndPublishPublicFeedCaches();
    }

    public void doubleDeleteAll(long delayMillis) {
        doubleDeleteAndPublishPublicFeedCaches(delayMillis);
    }

    public void deleteMyFeedCaches(long userId) {
        Set<String> keys = redis.keys("feed:mine:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public void doubleDeleteMy(long userId, long delayMillis) {
        deleteMyFeedCaches(userId);
        try {
            Thread.sleep(Math.max(delayMillis, 50));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        deleteMyFeedCaches(userId);
    }
}
