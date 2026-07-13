package com.tongji.admin.service;

import com.tongji.admin.dto.AdminKnowPostItem;
import com.tongji.admin.dto.PageResult;
import com.tongji.admin.mapper.AdminKnowPostMapper;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
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
     */
    @Transactional
    public void updateVisibility(long id, String visible) {
        requireExists(id);
        adminKnowPostMapper.updateVisibility(id, visible);
    }

    /**
     * 管理员设置置顶。
     */
    @Transactional
    public void updateTop(long id, boolean isTop) {
        requireExists(id);
        adminKnowPostMapper.updateTop(id, isTop);
    }

    /**
     * 管理员软删除。
     */
    @Transactional
    public void delete(long id) {
        requireExists(id);
        adminKnowPostMapper.softDelete(id);
    }

    private void requireExists(long id) {
        if (adminKnowPostMapper.findCreatorIdById(id) == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "知文不存在");
        }
    }
}
