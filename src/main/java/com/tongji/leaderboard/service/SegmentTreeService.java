package com.tongji.leaderboard.service;

/**
 * 线段树区间计数服务。
 */
public interface SegmentTreeService {

    /**
     * 按 old/new 分数更新路径计数。
     */
    void updatePath(String rankName, long oldScore, long newScore);

    /**
     * 按分数估算用户名次。
     */
    long estimateRank(String rankName, long score);
}
