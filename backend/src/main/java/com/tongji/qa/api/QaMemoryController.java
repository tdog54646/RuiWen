package com.tongji.qa.api;

import com.tongji.auth.token.JwtService;
import com.tongji.qa.api.dto.MemoryCreateRequest;
import com.tongji.qa.api.dto.MemoryResponse;
import com.tongji.qa.api.dto.MemoryUpdateRequest;
import com.tongji.qa.service.QaMemoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户记忆 REST 接口：结构化记忆条目的 CRUD + 一键 AI 重新生成。
 */
@RestController
@RequestMapping("/api/qa/memories")
@RequiredArgsConstructor
public class QaMemoryController {

    private final QaMemoryService memoryService;
    private final JwtService jwtService;

    /** 当前用户全部记忆条目（按 category 分组排序）。 */
    @GetMapping
    public List<MemoryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return memoryService.listMemories(jwtService.extractUserId(jwt));
    }

    /** 手动新增一条记忆（source=manual）。 */
    @PostMapping
    public MemoryResponse create(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody MemoryCreateRequest request) {
        return memoryService.createMemory(jwtService.extractUserId(jwt), request);
    }

    /** 编辑记忆条目（字段可选）。 */
    @PatchMapping("/{id}")
    public MemoryResponse update(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable("id") String id,
                                 @RequestBody MemoryUpdateRequest request) {
        return memoryService.updateMemory(jwtService.extractUserId(jwt), id, request);
    }

    /** 删除记忆条目。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") String id) {
        memoryService.deleteMemory(jwtService.extractUserId(jwt), id);
    }

    /** 一键 AI 重新生成（基于最近对话总结，替换 auto 条目，保留 manual）。 */
    @PostMapping("/regenerate")
    public List<MemoryResponse> regenerate(@AuthenticationPrincipal Jwt jwt) {
        return memoryService.regenerateMemories(jwtService.extractUserId(jwt));
    }
}
