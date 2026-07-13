package com.tongji.admin.api;

import com.tongji.admin.dto.DashboardStatsResponse;
import com.tongji.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台仪表盘接口。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    /**
     * 获取仪表盘统计数据。
     *
     * @return 统计响应。
     */
    @GetMapping("/stats")
    public DashboardStatsResponse stats() {
        return dashboardService.stats();
    }
}
