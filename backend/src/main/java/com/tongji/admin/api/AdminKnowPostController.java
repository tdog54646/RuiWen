package com.tongji.admin.api;

import com.tongji.admin.dto.AdminKnowPostItem;
import com.tongji.admin.dto.PageResult;
import com.tongji.admin.dto.UpdateTopRequest;
import com.tongji.admin.dto.UpdateVisibilityRequest;
import com.tongji.admin.service.AdminKnowPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 后台内容审核接口（知文）。
 */
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminKnowPostController {

    private final AdminKnowPostService adminKnowPostService;

    /**
     * 知文列表/搜索。
     *
     * @param keyword   标题关键字。
     * @param status    状态筛选（draft/published/deleted/...）。
     * @param visible   可见性筛选（public/private/...）。
     * @param creatorId 作者 ID 筛选。
     * @param page      页码。
     * @param size      每页大小。
     * @return 分页结果。
     */
    @GetMapping
    public PageResult<AdminKnowPostItem> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String visible,
                                              @RequestParam(required = false) Long creatorId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return adminKnowPostService.list(keyword, status, visible, creatorId, page, size);
    }

    /**
     * 修改知文可见性（管理员旁路）。
     */
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Void> updateVisibility(@PathVariable long id,
                                                 @Valid @RequestBody UpdateVisibilityRequest request) {
        adminKnowPostService.updateVisibility(id, request.visible());
        return ResponseEntity.noContent().build();
    }

    /**
     * 修改知文置顶（管理员旁路）。
     */
    @PatchMapping("/{id}/top")
    public ResponseEntity<Void> updateTop(@PathVariable long id,
                                          @Valid @RequestBody UpdateTopRequest request) {
        adminKnowPostService.updateTop(id, request.isTop());
        return ResponseEntity.noContent().build();
    }

    /**
     * 软删除知文（管理员旁路）。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        adminKnowPostService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
