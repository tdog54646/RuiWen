package com.tongji.leaderboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 排行榜模块配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "leaderboard")
public class LeaderboardProperties {

    /** Top 榜最大保留数量。 */
    private int topNMaxSize = 10000;
    /** Top 接口 limit 最大值。 */
    private int topQueryLimitMax = 200;
    /** 批量查 userIds 最大长度。 */
    private int batchLimit = 200;
    /** 线段树最小分值。 */
    private long segmentMinScore = 1;
    /** 线段树最大分值。 */
    private long segmentMaxScore = 100000;
    /** 线段树叶子区间宽度。 */
    private long segmentBucketSize = 100;
    /** 幂等去重键过期时间。 */
    private Duration dedupTtl = Duration.ofDays(2);
}
