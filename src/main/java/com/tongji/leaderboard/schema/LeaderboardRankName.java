package com.tongji.leaderboard.schema;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 外部参数到内部 rankName 的组装与校验。
 */
public final class LeaderboardRankName {

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,31}$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private LeaderboardRankName() {
    }

    /**
     * 组装内部榜单名：{type}:daily:{yyyyMMdd}。
     */
    public static String build(String leaderboardType, String date) {
        validate(leaderboardType, date);
        return leaderboardType + ":daily:" + date;
    }

    /**
     * 校验 leaderboardType 与 date 是否合法。
     */
    public static void validate(String leaderboardType, String date) {
        if (leaderboardType == null || !TYPE_PATTERN.matcher(leaderboardType).matches()) {
            throw new IllegalArgumentException("leaderboardType 参数非法");
        }
        if (date == null) {
            throw new IllegalArgumentException("date 参数非法");
        }
        try {
            LocalDate.parse(date, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("date 参数非法");
        }
    }
}
