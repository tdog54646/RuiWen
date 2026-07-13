package com.tongji.admin.service;

import com.tongji.admin.dto.PageResult;
import com.tongji.auth.audit.LoginLog;
import com.tongji.auth.audit.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 后台登录审计服务。
 */
@Service
@RequiredArgsConstructor
public class AdminLoginLogService {

    private final LoginLogMapper loginLogMapper;

    /**
     * 审计列表查询。
     *
     * @param userId     用户 ID 筛选（可空）。
     * @param identifier 标识模糊筛选（可空）。
     * @param status     状态筛选（SUCCESS/FAILED，可空）。
     * @param channel    渠道筛选（PASSWORD/CODE/REGISTER，可空）。
     * @param start      起始时间（可空）。
     * @param end        截止时间（可空）。
     * @param page       页码。
     * @param size       每页大小。
     * @return 分页结果。
     */
    public PageResult<LoginLog> list(Long userId, String identifier, String status, String channel,
                                     Instant start, Instant end, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        int offset = (p - 1) * s;
        long total = loginLogMapper.countPage(userId, identifier, status, channel, start, end);
        List<LoginLog> items = loginLogMapper.listPage(userId, identifier, status, channel, start, end, offset, s);
        return new PageResult<>(items, total, p, s);
    }
}
