package com.tongji.leaderboard.api.error;

/**
 * 排行榜模块业务异常。
 */
public class LeaderboardException extends RuntimeException {

    private final LeaderboardErrorCode errorCode;

    /**
     * 按错误码构造异常。
     */
    public LeaderboardException(LeaderboardErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 按错误码和自定义信息构造异常。
     */
    public LeaderboardException(LeaderboardErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 获取异常对应业务码。
     */
    public LeaderboardErrorCode getErrorCode() {
        return errorCode;
    }
}
