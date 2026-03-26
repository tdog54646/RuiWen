package com.tongji.leaderboard.api.dto.common;

/**
 * 排行榜模块统一响应包裹。
 */
public record ApiEnvelope<T>(
        int code,
        String message,
        String requestId,
        T data) {

    /**
     * 构造成功响应。
     */
    public static <T> ApiEnvelope<T> ok(String requestId, T data) {
        return new ApiEnvelope<>(0, "OK", requestId, data);
    }

    /**
     * 构造失败响应。
     */
    public static <T> ApiEnvelope<T> fail(int code, String message, String requestId) {
        return new ApiEnvelope<>(code, message, requestId, null);
    }
}
