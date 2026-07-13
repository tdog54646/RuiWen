package com.tongji.admin.api;

import com.tongji.admin.dto.PageResult;
import com.tongji.admin.service.AdminLoginLogService;
import com.tongji.auth.audit.LoginLog;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 后台登录审计接口。
 */
@RestController
@RequestMapping("/api/admin/audit/login-logs")
@RequiredArgsConstructor
public class AdminLoginLogController {

    private final AdminLoginLogService loginLogService;

    /**
     * 登录审计列表。
     *
     * @param userId     用户 ID 筛选。
     * @param identifier 标识模糊筛选。
     * @param status     状态筛选（SUCCESS/FAILED）。
     * @param channel    渠道筛选（PASSWORD/CODE/REGISTER）。
     * @param start      起始时间（ISO-8601）。
     * @param end        截止时间（ISO-8601）。
     * @param page       页码。
     * @param size       每页大小。
     * @return 分页结果。
     */
    @GetMapping
    public PageResult<LoginLog> list(@RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) String identifier,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String channel,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return loginLogService.list(userId, identifier, status, channel, start, end, page, size);
    }
}
