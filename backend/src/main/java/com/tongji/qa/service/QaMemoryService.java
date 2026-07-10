package com.tongji.qa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.qa.api.dto.MemoryCreateRequest;
import com.tongji.qa.api.dto.MemoryResponse;
import com.tongji.qa.api.dto.MemoryUpdateRequest;
import com.tongji.qa.config.QaProperties;
import com.tongji.qa.mapper.QaMessageMapper;
import com.tongji.qa.mapper.UserMemoryMapper;
import com.tongji.qa.model.QaMessage;
import com.tongji.qa.model.UserMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户记忆服务：结构化记忆条目的 CRUD，以及基于对话历史的 AI 自动总结（重新生成）。
 * <p>source=auto 条目由 AI 总结生成，source=manual 由用户手写。重新生成时仅替换 auto 条目，
 * manual 条目始终保留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaMemoryService {

    private final UserMemoryMapper memoryMapper;
    private final QaMessageMapper messageMapper;
    private final QaPromptAssembler promptAssembler;
    private final ChatClient chatClient;
    private final SnowflakeIdGenerator idGen;
    private final QaProperties properties;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MemoryResponse> listMemories(long userId) {
        return memoryMapper.listByUser(userId).stream().map(MemoryResponse::of).toList();
    }

    @Transactional
    public MemoryResponse createMemory(long userId, MemoryCreateRequest req) {
        if (req.content() == null || req.content().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不能为空");
        }
        Instant now = Instant.now();
        UserMemory m = UserMemory.builder()
                .id(idGen.nextId()).userId(userId)
                .category((req.category() == null || req.category().isBlank()) ? "其他" : req.category().trim())
                .content(req.content().trim())
                .source("manual").enabled(true)
                .createdAt(now).updatedAt(now).build();
        memoryMapper.insert(m);
        return MemoryResponse.of(m);
    }

    @Transactional
    public MemoryResponse updateMemory(long userId, String id, MemoryUpdateRequest req) {
        long mid = parseId(id);
        UserMemory existing = memoryMapper.findById(mid);
        if (existing == null || existing.getUserId() == null
                || existing.getUserId().longValue() != userId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆条目不存在或无访问权限");
        }
        UserMemory update = UserMemory.builder()
                .id(mid).userId(userId)
                .category(req.category())
                .content(req.content())
                .enabled(req.enabled())
                .build();
        memoryMapper.update(update);
        return MemoryResponse.of(memoryMapper.findById(mid));
    }

    @Transactional
    public void deleteMemory(long userId, String id) {
        long mid = parseId(id);
        int n = memoryMapper.delete(mid, userId);
        if (n == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆条目不存在或无访问权限");
        }
    }

    // -------------------------------------------------------------------------
    // AI 重新生成（删除旧 auto 条目，基于最近对话重新总结）
    // -------------------------------------------------------------------------

    /**
     * 一键 AI 重新生成用户记忆。
     * <p>取该用户最近 N 条对话（跨会话），调 LLM 总结为结构化条目，
     * 删除旧 source=auto 条目后批量插入新条目（manual 条目保留）。
     * <p>注：LLM 调用在事务外执行，避免长时间占用数据库连接；delete+batch 各自提交，
     * 失败时由后续自动更新补偿（最终一致）。
     */
    public List<MemoryResponse> regenerateMemories(long userId) {
        int window = properties.getMemory().getSummaryWindow();
        List<QaMessage> recent = messageMapper.listRecentByUser(userId, window);
        if (recent == null || recent.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "暂无对话记录，无法生成用户记忆");
        }
        Collections.reverse(recent); // 倒序 -> 正序
        String prompt = promptAssembler.buildMemorySummaryPrompt(buildDialog(recent));

        String raw;
        try {
            raw = chatClient.prompt()
                    .user(prompt)
                    .options(DeepSeekChatOptions.builder()
                            .model("deepseek-v4-flash")
                            .temperature(0.4)
                            .maxTokens(properties.getMemory().getSummaryMaxTokens())
                            .build())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("Memory summary LLM call failed (user={}): {}", userId, e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "记忆生成失败，请稍后重试");
        }

        List<UserMemory> items = parseItems(userId, raw);
        if (items.isEmpty()) {
            log.warn("Memory summary produced no items (user={}), raw={}", userId, truncate(raw, 200));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未能从对话中提炼出用户记忆，请稍后重试");
        }

        memoryMapper.deleteAutoByUser(userId);
        memoryMapper.batchInsert(items);
        log.info("Regenerated {} memory items for user {}", items.size(), userId);
        return memoryMapper.listByUser(userId).stream().map(MemoryResponse::of).toList();
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    private String buildDialog(List<QaMessage> msgs) {
        StringBuilder sb = new StringBuilder();
        for (QaMessage m : msgs) {
            String role = "assistant".equalsIgnoreCase(m.getRole()) ? "AI" : "用户";
            sb.append(role).append("：").append(safe(m.getContent())).append("\n");
        }
        return sb.toString();
    }

    /** 容错解析 LLM 输出为记忆条目列表（兼容代码块包裹与前后缀噪声）。包级可见以便单测。 */
    List<UserMemory> parseItems(long userId, String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = raw.trim();
        // 去掉可能的 ```json ... ``` 包裹
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl > 0) {
                text = text.substring(nl + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return List.of();
        }
        String json = text.substring(start, end + 1);
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) {
                return List.of();
            }
            Instant now = Instant.now();
            List<UserMemory> result = new ArrayList<>();
            for (JsonNode el : arr) {
                JsonNode c = el.get("category");
                JsonNode ct = el.get("content");
                String category = c != null ? c.asText().trim() : "";
                String content = ct != null ? ct.asText().trim() : "";
                if (category.isEmpty() || content.isEmpty()) {
                    continue;
                }
                if (content.length() > 500) {
                    content = content.substring(0, 500);
                }
                result.add(UserMemory.builder()
                        .id(idGen.nextId()).userId(userId)
                        .category(category).content(content)
                        .source("auto").enabled(true)
                        .createdAt(now).updatedAt(now).build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Parse memory summary JSON failed: {}", e.getMessage());
            return List.of();
        }
    }

    private long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的 ID: " + id);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
