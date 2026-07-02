package com.tongji.leaderboard.schema;

/**
 * 排行榜 Redis 键生成器。
 */
public final class LeaderboardKeys {

    private LeaderboardKeys() {
    }

    /**
     * Top 精确榜 ZSET 键。
     */
    public static String zsetKey(String rankName) {
        return "lb:zset:" + rankName;
    }

    /**
     * 线段树统计 Hash 键。
     */
    public static String segmentKey(String rankName) {
        return "lb:seg:" + rankName;
    }

    /**
     * 作者今日分数底座键（ownerId）。
     */
    public static String userCountKey(String rankName, long ownerId) {
        return "lb:ucnt:" + rankName + ":" + ownerId;
    }

    /**
     * 作者未刷写增量聚合键（ownerId）。
     */
    public static String aggKey(String rankName, long ownerId) {
        return "lb:agg:" + rankName + ":" + ownerId;
    }

    /**
     * 事件幂等去重键。
     */
    public static String dedupKey(String eventId) {
        return "leaderboard:event:dedup:" + eventId;
    }
}
