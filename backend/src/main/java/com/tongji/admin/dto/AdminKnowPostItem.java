package com.tongji.admin.dto;

import java.time.Instant;

/**
 * 后台内容审核列表项（知文）。
 * <p>注意：知文 ID 为雪花算法生成（超过 JS Number.MAX_SAFE_INTEGER），
 * 故用 {@code String} 承载，避免经 JSON 数字序列化后在浏览器端精度丢失。</p>
 */
public record AdminKnowPostItem(
        String id,
        String title,
        String description,
        Long creatorId,
        String creatorNickname,
        String status,
        String visible,
        Boolean isTop,
        String type,
        String tags,
        Instant createTime,
        Instant publishTime
) {
}
