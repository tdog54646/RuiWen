package com.tongji.knowpost.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.counter.event.CounterEvent;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import com.tongji.knowpost.service.FeedCacheService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Feed 缓存主动更新监听器。
 *
 * <p>当点赞/收藏计数发生变化时，不会简单删除缓存（那样会引发缓存击穿），
 * 而是找到所有受影响的 Feed 分页缓存条目，直接修正其中对应帖子的 like/fav 数值。
 * 这样做既保证了数据立即可见，又避免了重建缓存的性能开销。</p>
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li>更新帖子作者的被点赞/被收藏总数</li>
 *   <li>更新 Redis 中该帖子的计数快照（feed:count:{eid}）</li>
 *   <li>通过 Redis Set 索引找到所有包含该帖子的 Feed 分页缓存 key</li>
 *   <li>逐一修正本地 Caffeine 缓存和 Redis 中的分页数据</li>
 * </ol>
 */
@Component
public class FeedCacheInvalidationListener {

    private final FeedCacheService feedCacheService;
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final com.tongji.counter.service.UserCounterService userCounterService;
    private final com.tongji.knowpost.mapper.KnowPostMapper knowPostMapper;

    public FeedCacheInvalidationListener(FeedCacheService feedCacheService,
                                         @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache,
                                         StringRedisTemplate redis,
                                         ObjectMapper objectMapper,
                                         com.tongji.counter.service.UserCounterService userCounterService,
                                         com.tongji.knowpost.mapper.KnowPostMapper knowPostMapper) {
        this.feedCacheService = feedCacheService;
        this.feedPublicCache = feedPublicCache;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.userCounterService = userCounterService;
        this.knowPostMapper = knowPostMapper;
    }

    /**
     * 监听计数变更事件，修正 Feed 三级缓存中的计数数据。
     *
     * <p>只处理 entityType 为 "knowpost" 且 metric 为 "like" 或 "fav" 的事件。
     * 修改操作（点赞/收藏）delta 为正，取消操作 delta 为负。</p>
     *
     * <h4>为什么查当前和上一个小时槽？</h4>
     * Feed 分页缓存按小时分片索引。缓存条目可能存在于当前小时槽或上一小时槽中
     * （跨小时边界时），所以两个都要查，避免遗漏。
     */
    @EventListener
    public void onCounterChanged(CounterEvent event) {
        // 只处理帖子的点赞/收藏事件
        if (!"knowpost".equals(event.getEntityType())) return;
        String metric = event.getMetric();
        if ("like".equals(metric) || "fav".equals(metric)) {
            String eid = event.getEntityId();
            int delta = event.getDelta();
            try {
                // 1. 更新帖子作者的被点赞/被收藏总数
                com.tongji.knowpost.model.KnowPost post = knowPostMapper.findById(Long.valueOf(eid));
                if (post != null && post.getCreatorId() != null) {
                    long owner = post.getCreatorId();
                    if ("like".equals(metric)) userCounterService.incrementLikesReceived(owner, delta);
                    if ("fav".equals(metric)) userCounterService.incrementFavsReceived(owner, delta);
                }
            } catch (Exception ignored) {}

            // 2. 更新 Redis 中该帖子的计数快照
            updateCountCache(eid, metric, delta);

            // 3. 查找所有包含该帖子的 Feed 分页缓存 key
            //    索引 key 格式: feed:public:index:{eid}:{小时槽}
            long hourSlot = System.currentTimeMillis() / 3600000L;
            Set<String> keys = new java.util.LinkedHashSet<>();  // LinkedHashSet 去重并保持插入顺序
            Set<String> cur = redis.opsForSet().members("feed:public:index:" + eid + ":" + hourSlot);
            if (cur != null) keys.addAll(cur);
            Set<String> prev = redis.opsForSet().members("feed:public:index:" + eid + ":" + (hourSlot - 1));
            if (prev != null) keys.addAll(prev);
            if (keys == null || keys.isEmpty()) return;

            // 4. 逐个修正缓存中的分页数据
            for (String key : keys) {
                // 4a. 修正本地 Caffeine 缓存（用户维度，需保留 liked/faved 标志）
                FeedPageResponse local = feedPublicCache.getIfPresent(key);
                if (local != null) {
                    FeedPageResponse updatedLocal = adjustPageCounts(local, eid, metric, delta, true);
                    feedPublicCache.put(key, updatedLocal);
                }
                // 4b. 修正 Redis 中的分页 JSON（公共数据，liked/faved 置 null）
                String cached = redis.opsForValue().get(key);
                if (cached != null) {
                    try {
                        FeedPageResponse resp = objectMapper.readValue(cached, FeedPageResponse.class);
                        FeedPageResponse updated = adjustPageCounts(resp, eid, metric, delta, false);
                        writePageJsonKeepingTtl(key, updated);
                    } catch (Exception ignored) {}
                } else {
                    // Redis 中已无此 key，从索引 set 中清理
                    redis.opsForSet().remove("feed:public:index:" + eid + ":" + hourSlot, key);
                }
            }
        }
    }

    /**
     * 更新 Redis 中帖子的计数快照（feed:count:{eid}）。
     * 存储 like 和 fav 两个计数字段，保留原有 TTL。
     */
    private void updateCountCache(String eid, String metric, int delta) {
        String cntKey = "feed:count:" + eid;
        String cntJson = redis.opsForValue().get(cntKey);
        Map<String, Long> cm = null;
        if (cntJson != null) {
            try {
                cm = objectMapper.readValue(cntJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Long>>() {});
            } catch (Exception ignored) {}
        }
        if (cm == null) cm = new LinkedHashMap<>();
        Long like = cm.getOrDefault("like", 0L);
        Long fav = cm.getOrDefault("fav", 0L);
        if ("like".equals(metric)) like = Math.max(0L, like + delta);
        if ("fav".equals(metric)) fav = Math.max(0L, fav + delta);
        cm.put("like", like);
        cm.put("fav", fav);
        try {
            String j = objectMapper.writeValueAsString(cm);
            // 保留原 key 的 TTL，避免缓存永不过期
            Long ttl = redis.getExpire(cntKey);
            if (ttl != null && ttl > 0) {
                redis.opsForValue().set(cntKey, j, java.time.Duration.ofSeconds(ttl));
            } else {
                redis.opsForValue().set(cntKey, j);
            }
        } catch (Exception ignored) {}
    }

    /**
     * 在 Feed 分页数据中修正指定帖子的 like/fav 计数。
     *
     * @param page               原始分页结果
     * @param eid                需要修正的帖子 ID
     * @param metric             指标名（"like" 或 "fav"）
     * @param delta              增量（+1 或 -1）
     * @param preserveUserFlags  是否保留 liked/faved 标志。
     *                           true 用于用户维度的本地缓存（Caffeine），
     *                           false 用于公共 Redis 缓存（这些字段对公共数据无意义）
     */
    private FeedPageResponse adjustPageCounts(FeedPageResponse page, String eid, String metric, int delta, boolean preserveUserFlags) {
        java.util.List<com.tongji.knowpost.api.dto.FeedItemResponse> items = new java.util.ArrayList<>(page.items().size());
        for (com.tongji.knowpost.api.dto.FeedItemResponse it : page.items()) {
            if (eid.equals(it.id())) {
                Long like = it.likeCount();
                Long fav = it.favoriteCount();
                if ("like".equals(metric)) like = Math.max(0L, (like == null ? 0L : like) + delta);
                if ("fav".equals(metric)) fav = Math.max(0L, (fav == null ? 0L : fav) + delta);
                // 公共缓存中 liked/faved 无意义，置 null；用户缓存中保留原值
                Boolean liked = preserveUserFlags ? it.liked() : null;
                Boolean faved = preserveUserFlags ? it.faved() : null;
                it = new com.tongji.knowpost.api.dto.FeedItemResponse(
                        it.id(), it.title(), it.description(), it.coverImage(),
                        it.tags(), it.authorAvatar(), it.authorNickname(), it.tagJson(),
                        like, fav, liked, faved, it.isTop());
            }
            items.add(it);
        }
        return new FeedPageResponse(items, page.page(), page.size(), page.hasMore());
    }

    /**
     * 将修正后的分页数据写回 Redis，保留原 key 的 TTL。
     * 避免写回时丢失过期时间导致缓存永不过期。
     */
    private void writePageJsonKeepingTtl(String key, FeedPageResponse page) {
        try {
            String json = objectMapper.writeValueAsString(page);
            Long ttl = redis.getExpire(key);
            if (ttl != null && ttl > 0) {
                redis.opsForValue().set(key, json, java.time.Duration.ofSeconds(ttl));
            } else {
                redis.opsForValue().set(key, json);
            }
        } catch (Exception ignored) {}
    }
}
