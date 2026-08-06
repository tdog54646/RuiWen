package com.tongji.admin.api;

import com.tongji.admin.dto.IndexStatsResponse;
import com.tongji.admin.dto.RebuildStatusResponse;
import com.tongji.admin.service.AdminIndexService;
import com.tongji.llm.rag.index.RagIndexManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台索引库管理（RAG 向量索引）。
 * <p>路径 {@code /api/admin/index/**} 自动受 ADMIN/SUPER_ADMIN 角色保护。
 */
@RestController
@RequestMapping("/api/admin/index")
@RequiredArgsConstructor
public class AdminIndexController {

    private final AdminIndexService adminIndexService;

    /** 索引统计：切片总数、已索引知文数、按可见性分布。 */
    @GetMapping("/rag/stats")
    public IndexStatsResponse stats() {
        return adminIndexService.stats();
    }

    /** 强制重建单个知文的向量切片，返回写入切片数。 */
    @PostMapping("/rag/posts/{id}/rebuild")
    public int rebuildPost(@PathVariable long id) {
        return adminIndexService.rebuildPost(id);
    }

    /** 删除单个知文的向量切片。 */
    @DeleteMapping("/rag/posts/{id}")
    public void deletePostIndex(@PathVariable long id) {
        adminIndexService.deletePostIndex(id);
    }

    /** 触发全量重建（异步），立即返回当前进度。 */
    @PostMapping("/rag/rebuild-all")
    public RebuildStatusResponse rebuildAll() {
        return adminIndexService.rebuildAll();
    }

    /** 查询全量重建进度。 */
    @GetMapping("/rag/rebuild-all/status")
    public RebuildStatusResponse rebuildAllStatus() {
        return adminIndexService.rebuildAllStatus();
    }

    /** 显式迁移到带 IK Analyzer 的版本化索引，并原子切换稳定别名。 */
    @PostMapping("/rag/migrate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public RagIndexManager.MigrationResult migrate() {
        return adminIndexService.migrateIndex();
    }

}
