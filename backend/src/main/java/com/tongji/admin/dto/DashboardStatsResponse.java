package com.tongji.admin.dto;

import java.util.Map;

/**
 * 仪表盘统计数据响应。
 */
public record DashboardStatsResponse(
        long totalUsers,
        long newUsersToday,
        long bannedUsers,
        long totalPosts,
        long publishedPosts,
        long loginsToday,
        /** 角色分布：USER / ADMIN / SUPER_ADMIN 的数量。 */
        Map<String, Long> roleDistribution,
        long totalConversations,
        long totalMessages,
        long totalMemories
) {
}
