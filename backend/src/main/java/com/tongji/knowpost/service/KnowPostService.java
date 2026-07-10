package com.tongji.knowpost.service;

import com.tongji.knowpost.api.dto.KnowPostDetailResponse;

import java.util.List;

/**
 * 知文业务接口。
 */
public interface KnowPostService {

    long createDraft(long creatorId);

    void confirmContent(long creatorId, long id, String objectKey, String etag, Long size, String sha256);

    void updatePost(long creatorId, long id, String objectKey, String etag, Long size, String sha256,
                    String title, Long tagId, List<String> tags, List<String> imgUrls,
                    String visible, Boolean isTop, String description);

    void updateMetadata(long creatorId, long id, String title, Long tagId, List<String> tags, List<String> imgUrls, String visible, Boolean isTop, String description);

    void publish(long creatorId, long id);

    void updateTop(long creatorId, long id, boolean isTop);

    void updateVisibility(long creatorId, long id, String visible);

    void delete(long creatorId, long id);

    KnowPostDetailResponse getDetail(long id, Long currentUserIdNullable);

    /**
     * 导出指定知文为 PDF。可见性规则同 {@link #getDetail}（公开或作者本人）。
     *
     * @param id                   知文 ID
     * @param currentUserIdNullable 当前用户 ID（匿名访问时为 null）
     * @return PDF 字节
     */
    byte[] exportPdf(long id, Long currentUserIdNullable);
}
