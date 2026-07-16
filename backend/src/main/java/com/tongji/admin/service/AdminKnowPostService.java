package com.tongji.admin.service;

import com.tongji.admin.dto.AdminKnowPostItem;
import com.tongji.admin.dto.PageResult;
import com.tongji.admin.mapper.AdminKnowPostMapper;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.knowpost.event.KnowPostEvent;
import com.tongji.relation.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 后台内容审核服务（管理员旁路：不校验作者）。
 */
@Service
@RequiredArgsConstructor
public class AdminKnowPostService {

    private final AdminKnowPostMapper adminKnowPostMapper;
    private final OutboxService outboxService;

    /**
     * 知文列表/搜索。
     */
    public PageResult<AdminKnowPostItem> list(String keyword, String status, String visible,
                                              Long creatorId, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        int offset = (p - 1) * s;
        long total = adminKnowPostMapper.countPage(keyword, status, visible, creatorId);
        List<AdminKnowPostItem> items = adminKnowPostMapper.listPage(keyword, status, visible, creatorId, offset, s);
        return new PageResult<>(items, total, p, s);
    }

    /**
     * 管理员设置可见性。
     * <p>复刻 {@code KnowPostServiceImpl#updateVisibility}：更新后通过 Outbox 异步重建搜索/RAG 索引，
     * 保证状态切换（public→private 等）后向量索引按新可见性重建。
     */
    @Transactional
    public void updateVisibility(long id, String visible) {
        requireExists(id);
        adminKnowPostMapper.updateVisibility(id, visible);
        String eventType = "public".equals(visible) ? "KnowPostPublic" : "KnowPostPrivate";
        enqueueKnowPostEvent(id, eventType, visible);
    }

    /**
     * 管理员设置置顶。
     * <p>置顶不影响正文与可见性，无需重建 RAG 索引（与用户侧 {@code updateTop} 一致，不发 Outbox 事件）。
     */
    @Transactional
    public void updateTop(long id, boolean isTop) {
        requireExists(id);
        adminKnowPostMapper.updateTop(id, isTop);
    }

    /**
     * 管理员软删除。
     * <p>复刻 {@code KnowPostServiceImpl#delete}：删除后通过 Outbox 异步清理搜索/RAG 索引切片。
     */
    @Transactional
    public void delete(long id) {
        requireExists(id);
        adminKnowPostMapper.softDelete(id);
        enqueueKnowPostEvent(id, "KnowPostDeleted", "delete");
    }

    /** 投递知文索引事件，与用户侧 {@code KnowPostServiceImpl#enqueueKnowPostEvent} 保持一致。 */
    private void enqueueKnowPostEvent(long id, String eventType, String op) {
        outboxService.insert("knowpost", id, eventType, new KnowPostEvent("knowpost", op, id));
    }

    private void requireExists(long id) {
        if (adminKnowPostMapper.findCreatorIdById(id) == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "知文不存在");
        }
    }
}
