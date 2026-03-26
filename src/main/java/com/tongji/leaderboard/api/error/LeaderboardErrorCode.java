package com.tongji.leaderboard.api.error;

/**
 * 排行榜模块错误码定义。
 */
public enum LeaderboardErrorCode {
    SUCCESS(0, "OK"),
    BAD_REQUEST(40001, "参数非法"),
    LEADERBOARD_NOT_FOUND(40004, "榜单不存在"),
    BATCH_LIMIT_EXCEEDED(40029, "批量请求超限"),
    INTERNAL_ERROR(50001, "内部服务错误"),
    COUNTER_SERVICE_UNAVAILABLE(50002, "计数服务不可用"),
    STORAGE_WRITE_FAILED(50003, "排行榜存储写入失败");

    private final int code;
    private final String defaultMessage;

    LeaderboardErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取业务错误码。
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取默认错误信息。
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
