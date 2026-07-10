package com.tongji.counter.service;

import java.util.List;
import java.util.Map;

public interface CounterService {
    /**
     * 点赞：仅在之前未点赞时置位并 +1。
     * @return 是否发生状态变化（true 表示这次操作生效）
     */
    boolean like(String entityType, String entityId, long userId);

    /**
     * 取消点赞：仅在之前已点赞时清位并 -1。
     * @return 是否发生状态变化（true 表示这次操作生效）
     */
    boolean unlike(String entityType, String entityId, long userId);

    /**
     * 收藏：仅在之前未收藏时置位并 +1。
     */
    boolean fav(String entityType, String entityId, long userId);

    /**
     * 取消收藏：仅在之前已收藏时清位并 -1。
     */
    boolean unfav(String entityType, String entityId, long userId);

    /**
     * 获取指定指标的计数。
     */
    Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics);

    Map<String, Map<String, Long>> getCountsBatch(String entityType, List<String> entityIds, List<String> metrics);

    /**
     * 清除实体的全部计数数据（位图分片/SDS 快照/聚合桶），并返回清除前的 like/fav 计数。
     * 用于实体删除时回收计数事实，避免后续基于位图的重建把已删实体的点赞/收藏误计入。
     * @return 清除前的计数（key: like/fav），供上游扣减作者维度计数
     */
    Map<String, Long> clearEntityCounts(String entityType, String entityId);

    /**
     * 判断是否点赞/收藏（位图）。
     */
    boolean isLiked(String entityType, String entityId, long userId);
    boolean isFaved(String entityType, String entityId, long userId);
}
