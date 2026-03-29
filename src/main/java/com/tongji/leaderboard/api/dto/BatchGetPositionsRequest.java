package com.tongji.leaderboard.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 批量查询用户名次请求体。
 */
public record BatchGetPositionsRequest(
        @NotBlank(message = "leaderboardType 不能为空")
        String leaderboardType,
        @NotBlank(message = "date 不能为空")
        String date,
        @NotEmpty(message = "userIds 不能为空")
        List<@NotNull(message = "userId 不能为空") @Positive(message = "userId 必须大于 0") Long> userIds) {
}
