package com.tongji.admin.service;

import com.tongji.admin.dto.AdminConversationItem;
import com.tongji.admin.dto.AdminMemoryItem;
import com.tongji.admin.dto.AdminMessageItem;
import com.tongji.admin.dto.PageResult;
import com.tongji.admin.mapper.AdminQaMapper;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 问答后台管理服务：会话审计（列表/查看消息/删除）、用户记忆管理（列表/开关/删除）。
 */
@Service
@RequiredArgsConstructor
public class AdminQaService {

    private final AdminQaMapper adminQaMapper;

    // ===== 会话 =====

    public PageResult<AdminConversationItem> listConversations(String keyword, Long userId,
                                                               boolean includeDeleted, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        int offset = (p - 1) * s;
        long total = adminQaMapper.countConversations(keyword, userId, includeDeleted);
        List<AdminConversationItem> items = adminQaMapper.listConversations(keyword, userId, includeDeleted, offset, s);
        return new PageResult<>(items, total, p, s);
    }

    @Transactional
    public void deleteConversation(long id) {
        if (adminQaMapper.findUserIdByConversation(id) == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "会话不存在");
        }
        adminQaMapper.softDeleteConversation(id);
    }

    // ===== 消息 =====

    public PageResult<AdminMessageItem> listMessages(long conversationId, int page, int size) {
        if (adminQaMapper.findUserIdByConversation(conversationId) == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "会话不存在");
        }
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        int offset = (p - 1) * s;
        long total = adminQaMapper.countMessages(conversationId);
        List<AdminMessageItem> items = adminQaMapper.listMessages(conversationId, offset, s);
        return new PageResult<>(items, total, p, s);
    }

    @Transactional
    public void deleteMessage(long id) {
        adminQaMapper.deleteMessage(id);
    }

    // ===== 用户记忆 =====

    public PageResult<AdminMemoryItem> listMemories(String keyword, Long userId, String source, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        int offset = (p - 1) * s;
        long total = adminQaMapper.countMemories(keyword, userId, source);
        List<AdminMemoryItem> items = adminQaMapper.listMemories(keyword, userId, source, offset, s);
        return new PageResult<>(items, total, p, s);
    }

    @Transactional
    public void updateMemoryEnabled(long id, boolean enabled) {
        adminQaMapper.updateMemoryEnabled(id, enabled);
    }

    @Transactional
    public void deleteMemory(long id) {
        adminQaMapper.deleteMemory(id);
    }
}
