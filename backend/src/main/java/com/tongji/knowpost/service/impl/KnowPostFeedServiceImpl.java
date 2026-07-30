package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.api.dto.FeedItemResponse;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostFeedRow;
import com.tongji.knowpost.service.KnowPostFeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** 首页、我的发布和用户主页 Feed 的统一缓存/回源实现。 */
@Service
public class KnowPostFeedServiceImpl implements KnowPostFeedService {

    private static final Logger log = LoggerFactory.getLogger(KnowPostFeedServiceImpl.class);
    private static final int LAYOUT_VERSION = 2;
    private static final int MAX_PAGE_SIZE = 50;

    private final KnowPostMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CounterService counterService;
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final Cache<String, FeedPageResponse> feedMineCache;
    private final HotKeyDetector hotKey;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();

    public KnowPostFeedServiceImpl(KnowPostMapper mapper,
                                   StringRedisTemplate redis,
                                   ObjectMapper objectMapper,
                                   CounterService counterService,
                                   @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache,
                                   @Qualifier("feedMineCache") Cache<String, FeedPageResponse> feedMineCache,
                                   HotKeyDetector hotKey) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.counterService = counterService;
        this.feedPublicCache = feedPublicCache;
        this.feedMineCache = feedMineCache;
        this.hotKey = hotKey;
    }

    @Override
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable) {
        PageBounds bounds = PageBounds.of(page, size);
        String key = "feed:public:" + bounds.size() + ":" + bounds.page() + ":v" + LAYOUT_VERSION;
        return loadPage(new FeedQuery(
                "public", key, bounds, currentUserIdNullable, false, 60, feedPublicCache,
                (limit, offset) -> mapper.listFeedPublic(limit, offset), true));
    }

    @Override
    public FeedPageResponse getMyPublished(long userId, int page, int size) {
        PageBounds bounds = PageBounds.of(page, size);
        String key = personalKey(userId, "all", bounds);
        return loadPage(new FeedQuery(
                "mine", key, bounds, userId, true, 30, feedMineCache,
                (limit, offset) -> mapper.listMyPublished(userId, limit, offset), false));
    }

    @Override
    public FeedPageResponse getUserPublicPublished(long userId, int page, int size, Long currentUserId) {
        PageBounds bounds = PageBounds.of(page, size);
        String key = personalKey(userId, "public", bounds);
        return loadPage(new FeedQuery(
                "user-public", key, bounds, currentUserId, true, 30, feedMineCache,
                (limit, offset) -> mapper.listUserPublicPublished(userId, limit, offset), false));
    }

    private FeedPageResponse loadPage(FeedQuery query) {
        FeedPageResponse base = query.localCache().getIfPresent(query.key());
        if (base != null) {
            recordHit(query);
            return withViewerState(base, query.viewerId());
        }

        base = readRedisPage(query.key());
        if (base != null) {
            query.localCache().put(query.key(), base);
            recordHit(query);
            return withViewerState(base, query.viewerId());
        }

        Object lock = singleFlight.computeIfAbsent(query.key(), ignored -> new Object());
        try {
            synchronized (lock) {
                base = query.localCache().getIfPresent(query.key());
                if (base == null) base = readRedisPage(query.key());
                if (base == null) base = loadFromDatabase(query);
                query.localCache().put(query.key(), base);
                return withViewerState(base, query.viewerId());
            }
        } finally {
            singleFlight.remove(query.key(), lock);
        }
    }

    private FeedPageResponse loadFromDatabase(FeedQuery query) {
        int offset = (query.bounds().page() - 1) * query.bounds().size();
        List<KnowPostFeedRow> rows = query.loader().load(query.bounds().size() + 1, offset);
        boolean hasMore = rows.size() > query.bounds().size();
        if (hasMore) rows = new ArrayList<>(rows.subList(0, query.bounds().size()));

        List<FeedItemResponse> items = mapRowsToBaseItems(rows, query.includeTop());
        FeedPageResponse page = new FeedPageResponse(
                items, query.bounds().page(), query.bounds().size(), hasMore);
        Duration ttl = Duration.ofSeconds(query.baseTtlSeconds()
                + ThreadLocalRandom.current().nextInt(Math.max(2, query.baseTtlSeconds() / 2)));
        writeRedisPage(query.key(), page, ttl);
        if (query.publicPage()) indexPublicPage(query.key(), items, ttl);
        hotKey.record(query.key());
        log.info("feed.{} source=db key={} page={} size={} hasMore={}",
                query.scope(), query.key(), query.bounds().page(), query.bounds().size(), hasMore);
        return page;
    }

    /** 缓存仅保存公共基础字段；liked/faved 始终在返回前按当前访问者覆盖。 */
    private List<FeedItemResponse> mapRowsToBaseItems(List<KnowPostFeedRow> rows, boolean includeTop) {
        List<String> ids = rows.stream().map(row -> String.valueOf(row.getId())).toList();
        Map<String, Map<String, Long>> counts = counterService.getCountsBatch(
                "knowpost", ids, List.of("like", "fav"));
        List<FeedItemResponse> items = new ArrayList<>(rows.size());
        for (KnowPostFeedRow row : rows) {
            String id = String.valueOf(row.getId());
            List<String> images = parseStringArray(row.getImgUrls());
            Map<String, Long> entityCounts = counts.getOrDefault(id, Map.of());
            items.add(new FeedItemResponse(
                    id,
                    row.getTitle(),
                    row.getDescription(),
                    images.isEmpty() ? null : images.getFirst(),
                    parseStringArray(row.getTags()),
                    row.getAuthorAvatar(),
                    row.getAuthorNickname(),
                    row.getAuthorTagJson(),
                    entityCounts.getOrDefault("like", 0L),
                    entityCounts.getOrDefault("fav", 0L),
                    null,
                    null,
                    includeTop ? row.getIsTop() : null,
                    row.getVisible()));
        }
        return items;
    }

    private FeedPageResponse withViewerState(FeedPageResponse base, Long viewerId) {
        List<FeedItemResponse> items = new ArrayList<>(base.items().size());
        for (FeedItemResponse item : base.items()) {
            boolean liked = viewerId != null && counterService.isLiked("knowpost", item.id(), viewerId);
            boolean faved = viewerId != null && counterService.isFaved("knowpost", item.id(), viewerId);
            items.add(new FeedItemResponse(
                    item.id(), item.title(), item.description(), item.coverImage(), item.tags(),
                    item.authorAvatar(), item.authorNickname(), item.tagJson(), item.likeCount(),
                    item.favoriteCount(), liked, faved, item.isTop(), item.visible()));
        }
        return new FeedPageResponse(items, base.page(), base.size(), base.hasMore());
    }

    private FeedPageResponse readRedisPage(String key) {
        String cached = redis.opsForValue().get(key);
        if (cached == null) return null;
        try {
            FeedPageResponse page = objectMapper.readValue(cached, FeedPageResponse.class);
            if (page.items() == null || page.items().stream()
                    .anyMatch(item -> item.likeCount() == null || item.favoriteCount() == null)) {
                redis.delete(key);
                return null;
            }
            return page;
        } catch (Exception e) {
            redis.delete(key);
            return null;
        }
    }

    private void writeRedisPage(String key, FeedPageResponse page, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(page), ttl);
        } catch (Exception e) {
            log.warn("feed cache serialization failed, key={}", key, e);
        }
    }

    private void indexPublicPage(String pageKey, List<FeedItemResponse> items, Duration ttl) {
        redis.opsForSet().add("feed:public:pages", pageKey);
        redis.expire("feed:public:pages", ttl.plusMinutes(5));
        long hourSlot = System.currentTimeMillis() / 3_600_000L;
        for (FeedItemResponse item : items) {
            String indexKey = "feed:public:index:" + item.id() + ":" + hourSlot;
            redis.opsForSet().add(indexKey, pageKey);
            redis.expire(indexKey, ttl.plusMinutes(1));
        }
    }

    private void recordHit(FeedQuery query) {
        hotKey.record(query.key());
        int target = query.publicPage()
                ? hotKey.ttlForPublic(query.baseTtlSeconds(), query.key())
                : hotKey.ttlForMine(query.baseTtlSeconds(), query.key());
        Long current = redis.getExpire(query.key());
        if (current != null && current >= 0 && current < target) {
            redis.expire(query.key(), Duration.ofSeconds(target));
        }
        log.info("feed.{} source=cache key={} page={} size={}",
                query.scope(), query.key(), query.bounds().page(), query.bounds().size());
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String personalKey(long userId, String visibility, PageBounds bounds) {
        return "feed:mine:" + userId + ":" + visibility + ":" + bounds.size() + ":"
                + bounds.page() + ":v" + LAYOUT_VERSION;
    }

    @FunctionalInterface
    private interface RowLoader {
        List<KnowPostFeedRow> load(int limit, int offset);
    }

    private record FeedQuery(String scope, String key, PageBounds bounds, Long viewerId,
                             boolean includeTop, int baseTtlSeconds,
                             Cache<String, FeedPageResponse> localCache,
                             RowLoader loader, boolean publicPage) {}

    private record PageBounds(int page, int size) {
        private static PageBounds of(int page, int size) {
            return new PageBounds(Math.max(1, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)));
        }
    }
}
