package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.api.dto.KnowPostDetailResponse;
import com.tongji.knowpost.event.KnowPostEvent;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.knowpost.service.FeedCacheService;
import com.tongji.knowpost.service.KnowPostService;
import com.tongji.relation.outbox.OutboxService;
import com.tongji.storage.config.OssProperties;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class KnowPostServiceImpl implements KnowPostService {

    private final KnowPostMapper mapper;
    @Resource
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;
    private final OssProperties ossProperties;
    private final FeedCacheService feedCacheService;
    private final CounterService counterService;
    private final com.tongji.counter.service.UserCounterService userCounterService;
    private final StringRedisTemplate redis;
    private final HotKeyDetector hotKey;
    private static final Logger log = LoggerFactory.getLogger(KnowPostServiceImpl.class);
    private static final int DETAIL_LAYOUT_VER = 1;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();
    private final OutboxService outboxService;

    /**
     * 创建草稿并返回新 ID。
     */
    @Transactional
    public long createDraft(long creatorId) {
        long id = idGen.nextId();
        Instant now = Instant.now();
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .status("draft")
                .type("image_text")
                .visible("public")
                .isTop(false)
                .createTime(now)
                .updateTime(now)
                .build();
        mapper.insertDraft(post);
        return id;
    }

    /**
     * 确认内容上传（写入 objectKey、etag、大小、校验和，并生成公共 URL）。
     */
    @Transactional
    public void confirmContent(long creatorId, long id, String objectKey, String etag, Long size, String sha256) {
        // 缓存双删（更新前先删除）
        feedCacheService.deleteAndPublishPublicFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .contentObjectKey(objectKey)
                .contentEtag(etag)
                .contentSize(size)
                .contentSha256(sha256)
                .contentUrl(publicUrl(objectKey))
                .updateTime(Instant.now())
                .build();
        int updated = mapper.updateContent(post);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        // 更新后再次删除，避免并发下写回旧值
        feedCacheService.doubleDeleteAndPublishPublicFeedCaches(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    /**
     * 编辑已发布知文：正文内容与元数据同事务更新，并通过 Outbox 异步重建搜索/RAG。
     */
    @Transactional
    public void updatePost(long creatorId, long id, String objectKey, String etag, Long size, String sha256,
                           String title, Long tagId, List<String> tags, List<String> imgUrls,
                           String visible, Boolean isTop, String description) {
        if (visible != null && !isValidVisible(visible)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见性取值非法");
        }
        feedCacheService.deleteAndPublishPublicFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);

        Instant now = Instant.now();
        KnowPost contentPost = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .contentObjectKey(objectKey)
                .contentEtag(etag)
                .contentSize(size)
                .contentSha256(sha256)
                .contentUrl(publicUrl(objectKey))
                .updateTime(now)
                .build();
        int contentUpdated = mapper.updateContent(contentPost);
        if (contentUpdated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知文不存在或无权限");
        }

        KnowPost metadataPost = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .title(title)
                .tagId(tagId)
                .tags(toJsonOrNull(tags))
                .imgUrls(toJsonOrNull(imgUrls))
                .visible(visible)
                .isTop(isTop)
                .description(description)
                .type("image_text")
                .updateTime(now)
                .build();
        int metadataUpdated = mapper.updateMetadata(metadataPost);
        if (metadataUpdated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知文不存在或无权限");
        }
        enqueueKnowPostEvent(id, "KnowPostUpdated", "update");

        feedCacheService.doubleDeleteAndPublishPublicFeedCaches(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    /**
     * 更新元数据：标题、标签、可见性、置顶、图片列表等。
     */
    @Transactional
    public void updateMetadata(long creatorId, long id, String title, Long tagId, List<String> tags, List<String> imgUrls, String visible, Boolean isTop, String description) {
        if (visible != null && !isValidVisible(visible)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见性取值非法");
        }
        // 缓存双删（更新前先删除）
        feedCacheService.deleteAndPublishPublicFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .title(title)
                .tagId(tagId)
                .tags(toJsonOrNull(tags))
                .imgUrls(toJsonOrNull(imgUrls))
                .visible(visible)
                .isTop(isTop)
                .description(description)
                .type("image_text")
                .updateTime(Instant.now())
                .build();

        int updated = mapper.updateMetadata(post);

        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        enqueueKnowPostEvent(id, "KnowPostUpdated", "update");
        // 更新后再次删除，避免并发下写回旧值
        feedCacheService.doubleDeleteAndPublishPublicFeedCaches(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    /**
     * 发布文章，设置状态与发布时间。
     */
    @Transactional
    public void publish(long creatorId, long id) {
        // 缓存双删（更新前先删除）
        feedCacheService.deleteAndPublishPublicFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
        int updated = mapper.publish(id, creatorId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        enqueueKnowPostEvent(id, "KnowPostInserted", "insert");
        try {
            userCounterService.incrementPosts(creatorId, 1);
        } catch (Exception ignored) {}

        // 更新后再次删除，避免并发下写回旧值
        feedCacheService.doubleDeleteAndPublishPublicFeedCaches(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    /**
     * 设置置顶。
     */
    @Transactional
    public void updateTop(long creatorId, long id, boolean isTop) {
        feedCacheService.deleteAndPublishPublicFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
        int updated = mapper.updateTop(id, creatorId, isTop);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        feedCacheService.doubleDeleteAndPublishPublicFeedCaches(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    /**
     * 设置可见性（权限）。
     */
    @Transactional
    public void updateVisibility(long creatorId, long id, String visible) {
        if (!isValidVisible(visible)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见性取值非法");
        }
        feedCacheService.deleteAndPublishPublicFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
        int updated = mapper.updateVisibility(id, creatorId, visible);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        String eventType = "public".equals(visible) ? "KnowPostPublic" : "KnowPostPrivate";
        enqueueKnowPostEvent(id, eventType, visible);
        feedCacheService.doubleDeleteAndPublishPublicFeedCaches(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    /**
     * 软删除。
     */
    @Transactional
    public void delete(long creatorId, long id) {
        feedCacheService.deleteAndPublishPublicFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
        int updated = mapper.softDelete(id, creatorId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        // 扣减作者维度计数（发文/获赞/获藏），并清理该文章的计数事实层（位图/SDS/聚合桶），
        // 避免后续基于位图的重建把已删文章的点赞/收藏再次计入。
        try {
            Map<String, Long> counts = counterService.clearEntityCounts("knowpost", String.valueOf(id));
            userCounterService.incrementPosts(creatorId, -1);
            long like = counts.getOrDefault("like", 0L);
            long fav = counts.getOrDefault("fav", 0L);
            if (like > 0) userCounterService.incrementLikesReceived(creatorId, -(int) like);
            if (fav > 0) userCounterService.incrementFavsReceived(creatorId, -(int) fav);
            // 同步清理 Feed 维度的计数快照，避免详情/Feed 读到陈旧赞数
            redis.delete("feed:count:" + id);
        } catch (Exception ignored) {}
        enqueueKnowPostEvent(id, "KnowPostDeleted", "delete");
        feedCacheService.doubleDeleteAndPublishPublicFeedCaches(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
        redis.delete("knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER);
    }

    private void enqueueKnowPostEvent(long id, String eventType, String op) {
        outboxService.insert("knowpost", id, eventType, new KnowPostEvent("knowpost", op, id));
    }

    private boolean isValidVisible(String visible) {
        if (visible == null) return false;
        return switch (visible) {
            case "public", "followers", "school", "private", "unlisted" -> true;
            default -> false;
        };
    }

    private String toJsonOrNull(List<String> list) {
        if (list == null) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 处理失败");
        }
    }

    private String publicUrl(String objectKey) {
        String publicDomain = ossProperties.getPublicDomain();
        if (publicDomain != null && !publicDomain.isBlank()) {
            return publicDomain.replaceAll("/$", "") + "/" + objectKey;
        }
        return "https://" + ossProperties.getBucket() + "." + ossProperties.getEndpoint() + "/" + objectKey;
    }

    /**
     * 获取知文详情（含作者信息、图片列表）。
     * - 公开策略：published + public 可匿名访问；否则需作者本人访问。
     * - 软删除内容不可见。
     */
    @Transactional(readOnly = true)
    public KnowPostDetailResponse getDetail(long id, Long currentUserIdNullable) {
        String pageKey = "knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER;
        String cached = redis.opsForValue().get(pageKey);
        if (cached != null) {
            if ("NULL".equals(cached)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不存在");
            }
            try {
                KnowPostDetailResponse base = objectMapper.readValue(cached, KnowPostDetailResponse.class);
                hotKey.record(pageKey);
                maybeExtendTtlDetail(pageKey);
                String cntKey = "feed:count:" + id;
                String cntJson = redis.opsForValue().get(cntKey);
                Long likeCount = base.likeCount();
                Long favoriteCount = base.favoriteCount();
                if (cntJson != null) {
                    try {
                        Map<String, Long> cm = objectMapper.readValue(cntJson, new TypeReference<Map<String, Long>>(){});
                        likeCount = cm.getOrDefault("like", likeCount == null ? 0L : likeCount);
                        favoriteCount = cm.getOrDefault("fav", favoriteCount == null ? 0L : favoriteCount);
                    } catch (Exception ignored) {}
                }
                boolean liked = currentUserIdNullable != null && counterService.isLiked("knowpost", String.valueOf(id), currentUserIdNullable);
                boolean faved = currentUserIdNullable != null && counterService.isFaved("knowpost", String.valueOf(id), currentUserIdNullable);
                log.info("detail source=page key={}", pageKey);
                return new KnowPostDetailResponse(
                        String.valueOf(id),
                        base.title(),
                        base.description(),
                        base.contentUrl(),
                        base.images(),
                        base.tags(),
                        base.authorId(),
                        base.authorAvatar(),
                        base.authorNickname(),
                        base.authorTagJson(),
                        likeCount,
                        favoriteCount,
                        liked,
                        faved,
                        base.isTop(),
                        base.visible(),
                        base.type(),
                        base.publishTime()
                );
            } catch (Exception ignored) {}
        }

        Object lock = singleFlight.computeIfAbsent(pageKey, k -> new Object());
        synchronized (lock) {
            String again = redis.opsForValue().get(pageKey);
            if (again != null && !"NULL".equals(again)) {
                try {
                    KnowPostDetailResponse base = objectMapper.readValue(again, KnowPostDetailResponse.class);
                    hotKey.record(pageKey);
                    maybeExtendTtlDetail(pageKey);
                    String cntKey = "feed:count:" + id;
                    String cntJson = redis.opsForValue().get(cntKey);
                    Long likeCount = base.likeCount();
                    Long favoriteCount = base.favoriteCount();
                    if (cntJson != null) {
                        try {
                            Map<String, Long> cm = objectMapper.readValue(cntJson, new TypeReference<Map<String, Long>>(){});
                            likeCount = cm.getOrDefault("like", likeCount == null ? 0L : likeCount);
                            favoriteCount = cm.getOrDefault("fav", favoriteCount == null ? 0L : favoriteCount);
                        } catch (Exception ignored) {}
                    }
                    boolean liked = currentUserIdNullable != null && counterService.isLiked("knowpost", String.valueOf(id), currentUserIdNullable);
                    boolean faved = currentUserIdNullable != null && counterService.isFaved("knowpost", String.valueOf(id), currentUserIdNullable);
                    log.info("detail source=page(after-flight) key={}", pageKey);
                    singleFlight.remove(pageKey);
                    return new KnowPostDetailResponse(
                            String.valueOf(id),
                            base.title(),
                            base.description(),
                            base.contentUrl(),
                            base.images(),
                            base.tags(),
                            base.authorId(),
                            base.authorAvatar(),
                            base.authorNickname(),
                            base.authorTagJson(),
                            likeCount,
                            favoriteCount,
                            liked,
                            faved,
                            base.isTop(),
                            base.visible(),
                            base.type(),
                            base.publishTime()
                    );
                } catch (Exception ignored) {}
            }

            KnowPostDetailRow row = mapper.findDetailById(id);
            if (row == null || "deleted".equals(row.getStatus())) {
                redis.opsForValue().set(pageKey, "NULL", java.time.Duration.ofSeconds(30 + java.util.concurrent.ThreadLocalRandom.current().nextInt(31)));
                singleFlight.remove(pageKey);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不存在");
            }

            boolean isPublic = "published".equals(row.getStatus()) && "public".equals(row.getVisible());
            boolean isOwner = currentUserIdNullable != null && row.getCreatorId() != null && currentUserIdNullable.equals(row.getCreatorId());
            if (!isPublic && !isOwner) {
                singleFlight.remove(pageKey);
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无权限查看");
            }

            List<String> images = parseStringArray(row.getImgUrls());
            List<String> tags = parseStringArray(row.getTags());
            Map<String, Long> counts = counterService.getCounts("knowpost", String.valueOf(row.getId()), List.of("like", "fav"));
            Long likeCount = counts.getOrDefault("like", 0L);
            Long favoriteCount = counts.getOrDefault("fav", 0L);

            KnowPostDetailResponse resp = new KnowPostDetailResponse(
                    String.valueOf(row.getId()),
                    row.getTitle(),
                    row.getDescription(),
                    row.getContentUrl(),
                    images,
                    tags,
                    String.valueOf(row.getCreatorId()),
                    row.getAuthorAvatar(),
                    row.getAuthorNickname(),
                    row.getAuthorTagJson(),
                    likeCount,
                    favoriteCount,
                    null,
                    null,
                    row.getIsTop(),
                    row.getVisible(),
                    row.getType(),
                    row.getPublishTime()
            );
            try {
                String json = objectMapper.writeValueAsString(resp);
                int baseTtl = 60;
                int jitter = java.util.concurrent.ThreadLocalRandom.current().nextInt(30);
                int target = hotKey.ttlForPublic(baseTtl, pageKey);
                redis.opsForValue().set(pageKey, json, java.time.Duration.ofSeconds(Math.max(target, baseTtl + jitter)));
                log.info("detail source=db key={}", pageKey);
            } catch (Exception ignored) {}
            boolean liked = currentUserIdNullable != null && counterService.isLiked("knowpost", String.valueOf(row.getId()), currentUserIdNullable);
            boolean faved = currentUserIdNullable != null && counterService.isFaved("knowpost", String.valueOf(row.getId()), currentUserIdNullable);
            singleFlight.remove(pageKey);
            return new KnowPostDetailResponse(
                    String.valueOf(row.getId()),
                    resp.title(),
                    resp.description(),
                    resp.contentUrl(),
                    resp.images(),
                    resp.tags(),
                    resp.authorId(),
                    resp.authorAvatar(),
                    resp.authorNickname(),
                    resp.authorTagJson(),
                    resp.likeCount(),
                    resp.favoriteCount(),
                    liked,
                    faved,
                    resp.isTop(),
                    resp.visible(),
                    resp.type(),
                    resp.publishTime()
            );
        }
    }

    private void maybeExtendTtlDetail(String key) {
        int baseTtl = 60;
        int target = hotKey.ttlForPublic(baseTtl, key);
        Long currentTtl = redis.getExpire(key);
        if (currentTtl < target) {
            redis.expire(key, java.time.Duration.ofSeconds(target));
        }
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
