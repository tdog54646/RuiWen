package com.tongji.leaderboard.api;

import com.tongji.leaderboard.api.dto.BatchGetPositionsRequest;
import com.tongji.leaderboard.api.dto.BatchGetPositionsResult;
import com.tongji.leaderboard.api.dto.RankPosition;
import com.tongji.leaderboard.api.dto.TopListResult;
import com.tongji.leaderboard.api.dto.common.ApiEnvelope;
import com.tongji.leaderboard.service.LeaderboardQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 排行榜对外 HTTP 查询接口。
 */
@RestController
@RequestMapping("/api/leaderboards")
@Validated
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardQueryService leaderboardQueryService;

    /**
     * 查询 Top 榜分页列表。
     */
    @GetMapping("/top")
    public ApiEnvelope<TopListResult> listTop(@RequestParam("leaderboardType") @NotBlank String leaderboardType,
                                              @RequestParam("date") @NotBlank String date,
                                              @RequestParam(value = "offset", defaultValue = "0") @Min(0) int offset,
                                              @RequestParam(value = "limit", defaultValue = "20") @Min(1) int limit,
                                              HttpServletRequest request) {
        TopListResult result = leaderboardQueryService.listTop(leaderboardType, date, offset, limit);
        return ApiEnvelope.ok(resolveRequestId(request), result);
    }

    /**
     * 查询单个用户当前名次。
     */
    @GetMapping("/users/{userId}/position")
    public ApiEnvelope<RankPosition> getUserPosition(@PathVariable("userId") @Min(1) long userId,
                                                     @RequestParam("leaderboardType") @NotBlank String leaderboardType,
                                                     @RequestParam("date") @NotBlank String date,
                                                     HttpServletRequest request) {
        RankPosition result = leaderboardQueryService.getUserPosition(leaderboardType, date, userId);
        return ApiEnvelope.ok(resolveRequestId(request), result);
    }

    /**
     * 批量查询多个用户当前名次。
     */
    @PostMapping("/users/positions:batchGet")
    public ApiEnvelope<BatchGetPositionsResult> batchGetUserPositions(@Valid @RequestBody BatchGetPositionsRequest body,
                                                                      HttpServletRequest request) {
        List<RankPosition> positions = leaderboardQueryService.batchGetUserPosition(
                body.leaderboardType(), body.date(), body.userIds());
        BatchGetPositionsResult result = new BatchGetPositionsResult(body.leaderboardType(), body.date(), positions);
        return ApiEnvelope.ok(resolveRequestId(request), result);
    }

    /**
     * 从请求头提取 requestId，不存在则生成新值。
     */
    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
