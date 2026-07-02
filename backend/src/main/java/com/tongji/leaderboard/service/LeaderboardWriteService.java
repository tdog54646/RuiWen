package com.tongji.leaderboard.service;

/**
 * 排行榜写链路服务。
 */
public interface LeaderboardWriteService {

    /**
     * 消费计数事件并更新排行榜（ownerId 为作者ID）。
     */
    void onCounterEvent(String eventId, long ownerId, String rankName, long delta);
}
