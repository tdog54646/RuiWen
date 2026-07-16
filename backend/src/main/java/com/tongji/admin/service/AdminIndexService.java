package com.tongji.admin.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.tongji.admin.dto.IndexStatsResponse;
import com.tongji.admin.dto.RebuildStatusResponse;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.llm.rag.index.RagIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台索引库管理服务（RAG 向量索引）。
 * <p>复用 {@link RagIndexService} 的单篇重建/删除；全量重建异步串行执行 + 内存进度。
 * 进度仅单实例有效（admin 低频运维操作可接受；多实例需改 Redis）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminIndexService {

    private final RagIndexService ragIndexService;
    private final KnowPostMapper knowPostMapper;
    private final ElasticsearchClient es;
    private final EsProperties esProps;

    private static final String META_VISIBLE = "metadata.visible";
    private static final String META_POSTID = "metadata.postId";

    // 全量重建进度（内存）
    private volatile boolean rebuildRunning = false;
    private volatile int rebuildTotal = 0;
    private final AtomicInteger rebuildDone = new AtomicInteger(0);
    private final AtomicInteger rebuildFailed = new AtomicInteger(0);
    private volatile Long rebuildStartedAt = null;
    private volatile Long rebuildFinishedAt = null;

    /** 索引统计：切片总数 + 按 visible 分布 + 去重知文数。 */
    public IndexStatsResponse stats() {
        if (!StringUtils.hasText(esProps.getIndex())) {
            return new IndexStatsResponse(0L, 0L, List.of());
        }
        try {
            // metadata 下的字符串字段被动态映射为 text（不可直接聚合），用其 .keyword 子字段聚合
            SearchResponse<Void> resp = es.search(s -> s
                            .index(esProps.getIndex())
                            .size(0)
                            .aggregations("by_visible", a -> a.terms(t -> t.field(META_VISIBLE + ".keyword").size(20)))
                            .aggregations("distinct_posts", a -> a.cardinality(c -> c.field(META_POSTID + ".keyword"))),
                    Void.class);

            long totalChunks = resp.hits().total() != null ? resp.hits().total().value() : 0L;

            List<IndexStatsResponse.VisibleBucket> buckets = new ArrayList<>();
            long distinctPosts = 0L;
            var aggs = resp.aggregations();
            if (aggs != null) {
                Aggregate byVisible = aggs.get("by_visible");
                if (byVisible != null && byVisible.isSterms()) {
                    for (StringTermsBucket b : byVisible.sterms().buckets().array()) {
                        String key = b.key().stringValue();
                        buckets.add(new IndexStatsResponse.VisibleBucket(key != null ? key : "unknown", b.docCount()));
                    }
                }
                Aggregate dp = aggs.get("distinct_posts");
                if (dp != null && dp.isCardinality()) {
                    distinctPosts = (long) dp.cardinality().value();
                }
            }
            return new IndexStatsResponse(totalChunks, distinctPosts, buckets);
        } catch (Exception e) {
            log.error("Index stats failed: {}", e.getMessage(), e);
            return new IndexStatsResponse(0L, 0L, List.of());
        }
    }

    /** 强制重建单篇，返回写入切片数。 */
    public int rebuildPost(long id) {
        return ragIndexService.rebuildSinglePost(id);
    }

    /** 删除单篇向量切片。 */
    public void deletePostIndex(long id) {
        ragIndexService.deletePostChunks(id);
    }

    /** 触发异步全量重建；已在运行则直接返回当前进度。 */
    public RebuildStatusResponse rebuildAll() {
        if (rebuildRunning) {
            return currentStatus();
        }
        List<Long> ids = knowPostMapper.listAllPublishedIds();
        rebuildTotal = ids.size();
        rebuildDone.set(0);
        rebuildFailed.set(0);
        rebuildStartedAt = System.currentTimeMillis();
        rebuildFinishedAt = null;
        rebuildRunning = true;

        CompletableFuture.runAsync(() -> {
            try {
                for (Long id : ids) {
                    try {
                        ragIndexService.rebuildSinglePost(id);
                        rebuildDone.incrementAndGet();
                    } catch (Exception e) {
                        rebuildFailed.incrementAndGet();
                        log.warn("Rebuild post {} failed: {}", id, e.getMessage());
                    }
                }
            } finally {
                rebuildFinishedAt = System.currentTimeMillis();
                rebuildRunning = false;
            }
        });

        return currentStatus();
    }

    /** 查询全量重建进度。 */
    public RebuildStatusResponse rebuildAllStatus() {
        return currentStatus();
    }

    private RebuildStatusResponse currentStatus() {
        String message = rebuildRunning ? "进行中" : (rebuildFinishedAt != null ? "已完成" : "未运行");
        return new RebuildStatusResponse(
                rebuildRunning, rebuildTotal, rebuildDone.get(), rebuildFailed.get(),
                rebuildStartedAt, rebuildFinishedAt, message);
    }
}
