package com.tongji.admin.service;

import com.tongji.admin.dto.DashboardStatsResponse;
import com.tongji.admin.mapper.AdminKnowPostMapper;
import com.tongji.auth.audit.LoginLogMapper;
import com.tongji.user.domain.UserRole;
import com.tongji.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仪表盘服务：聚合用户、知文、登录审计统计。
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserService userService;
    private final AdminKnowPostMapper knowPostMapper;
    private final LoginLogMapper loginLogMapper;
    private final com.tongji.admin.mapper.AdminQaMapper adminQaMapper;

    /**
     * 获取仪表盘统计数据。
     *
     * @return 统计响应。
     */
    public DashboardStatsResponse stats() {
        Instant startOfTodayUtc = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        Map<String, Long> roleDistribution = new LinkedHashMap<>();
        roleDistribution.put(UserRole.USER, userService.countByRole(UserRole.USER));
        roleDistribution.put(UserRole.ADMIN, userService.countByRole(UserRole.ADMIN));
        roleDistribution.put(UserRole.SUPER_ADMIN, userService.countByRole(UserRole.SUPER_ADMIN));

        return new DashboardStatsResponse(
                userService.countAll(),
                userService.countCreatedSince(startOfTodayUtc),
                userService.countSearchUsers(null, null, "BANNED"),
                knowPostMapper.countAll(),
                knowPostMapper.countPublished(),
                loginLogMapper.countSince(startOfTodayUtc),
                roleDistribution,
                adminQaMapper.countConversationsAll(),
                adminQaMapper.countMessagesAll(),
                adminQaMapper.countMemoriesAll()
        );
    }
}
