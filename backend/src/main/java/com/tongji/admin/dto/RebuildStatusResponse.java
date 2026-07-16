package com.tongji.admin.dto;

/** 全量重建进度（内存维护，单实例有效）。 */
public record RebuildStatusResponse(
        boolean running,
        int total,
        int done,
        int failed,
        Long startedAt,
        Long finishedAt,
        String message
) {}
