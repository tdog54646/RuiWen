package com.tongji.admin.api;

import com.tongji.admin.dto.AdminConversationItem;
import com.tongji.admin.dto.AdminMemoryItem;
import com.tongji.admin.dto.AdminMessageItem;
import com.tongji.admin.dto.PageResult;
import com.tongji.admin.dto.UpdateMemoryEnabledRequest;
import com.tongji.admin.service.AdminQaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI 对话后台管理接口：会话审计、消息查看、用户记忆管理。
 */
@RestController
@RequestMapping("/api/admin/qa")
@RequiredArgsConstructor
public class AdminQaController {

    private final AdminQaService adminQaService;

    // ===== 会话 =====

    /**
     * 会话列表/搜索。
     *
     * @param keyword        标题关键字。
     * @param userId         用户筛选。
     * @param includeDeleted 是否包含已删除会话。
     */
    @GetMapping("/conversations")
    public PageResult<AdminConversationItem> listConversations(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Long userId,
                                                               @RequestParam(defaultValue = "false") boolean includeDeleted,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return adminQaService.listConversations(keyword, userId, includeDeleted, page, size);
    }

    /**
     * 会话内消息列表（按时间正序）。
     */
    @GetMapping("/conversations/{id}/messages")
    public PageResult<AdminMessageItem> listMessages(@PathVariable long id,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return adminQaService.listMessages(id, page, size);
    }

    /**
     * 管理员软删除会话。
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable long id) {
        adminQaService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 管理员删除单条消息（违规内容清理）。
     */
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable long id) {
        adminQaService.deleteMessage(id);
        return ResponseEntity.noContent().build();
    }

    // ===== 用户记忆 =====

    /**
     * 用户记忆列表/搜索。
     *
     * @param keyword 内容关键字。
     * @param userId  用户筛选。
     * @param source  来源筛选（auto/manual）。
     */
    @GetMapping("/memories")
    public PageResult<AdminMemoryItem> listMemories(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String source,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return adminQaService.listMemories(keyword, userId, source, page, size);
    }

    /**
     * 切换记忆启用状态。
     */
    @PatchMapping("/memories/{id}/enabled")
    public ResponseEntity<Void> updateMemoryEnabled(@PathVariable long id,
                                                    @Valid @RequestBody UpdateMemoryEnabledRequest request) {
        adminQaService.updateMemoryEnabled(id, request.enabled());
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除记忆。
     */
    @DeleteMapping("/memories/{id}")
    public ResponseEntity<Void> deleteMemory(@PathVariable long id) {
        adminQaService.deleteMemory(id);
        return ResponseEntity.noContent().build();
    }
}
