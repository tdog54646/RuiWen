package com.tongji.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.knowpost.service.KnowPostService;
import com.tongji.qa.config.QaProperties;
import com.tongji.storage.OssStorageService;
import com.tongji.storage.api.dto.ContentUploadResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * AI 录入文章草稿生成服务。
 * <p>给定标题与主题要点，用 LLM 生成 Markdown 正文，上传 OSS，落库为待确认草稿（status=draft）。
 * 供 QA 工具 draft_post 调用。
 *
 * <p><b>正确性</b>：草稿阶段直接用 {@link KnowPostMapper} 写入（insertDraft / updateContent / updateMetadata
 * 的原始 SQL），<b>不</b>走 {@code KnowPostService.confirmContent / updateMetadata}——那两个 service 方法带有
 * 清缓存、插 outbox 等"发布态"副作用，在 draft 阶段是空清 / 空跑的污染。真正的缓存清理、outbox
 * （KnowPostInserted）、发文计数都留给 publish 触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostDraftService {

    private static final String DRAFT_TEMPLATE_PATH = "qa-prompts/post-draft.md";
    private static final int PREVIEW_MAX = 300;

    private final ChatClient chatClient;
    private final KnowPostService knowPostService;
    private final KnowPostMapper knowPostMapper;
    private final KnowPostDescriptionService descriptionService;
    private final OssStorageService ossStorageService;
    private final ObjectMapper objectMapper;
    private final QaProperties properties;

    private String draftTemplate;

    @PostConstruct
    void init() throws IOException {
        this.draftTemplate = readClasspath(DRAFT_TEMPLATE_PATH);
    }

    /**
     * 生成正文 → 建草稿行 → 上传 OSS → 写正文指针 → 写元数据，返回草稿摘要。
     * 全程仅落 DB + OSS，不发 outbox、不清缓存、不计数（这些都等 publish）。
     */
    public DraftResult createDraft(long userId, String title, String topic, List<String> tags) {
        String content = generateContent(title, topic, tags);
        Instant now = Instant.now();

        // 1) 建草稿行（KnowPostService.createDraft 仅 insertDraft，无副作用）
        long postId = knowPostService.createDraft(userId);

        // 2) 上传正文到 OSS（uploadPostContent 一并返回 contentUrl）
        ContentUploadResult upload = ossStorageService.uploadPostContent(postId, content);

        // 3) 写正文指针（mapper 直写，无缓存清理）
        knowPostMapper.updateContent(KnowPost.builder()
                .id(postId).creatorId(userId)
                .contentUrl(upload.contentUrl())
                .contentObjectKey(upload.objectKey())
                .contentEtag(upload.etag())
                .contentSize(upload.size())
                .contentSha256(upload.sha256())
                .updateTime(now)
                .build());

        // 4) 生成 description（保持同步）
        String description = safeDescription(content);

        // 5) 写元数据（mapper 直写，无 outbox、无缓存清理）
        knowPostMapper.updateMetadata(KnowPost.builder()
                .id(postId).creatorId(userId)
                .title(title)
                .tags(tagsToJson(tags))
                .visible("public")
                .isTop(false)
                .description(description)
                .type("image_text")
                .updateTime(Instant.now())
                .build());

        String preview = content.length() > PREVIEW_MAX
                ? content.substring(0, PREVIEW_MAX) + "…" : content;
        log.info("AI draft created: user={}, postId={}, title={}", userId, postId, title);
        return new DraftResult(String.valueOf(postId), title, tags, preview);
    }

    /** 用 LLM 生成 Markdown 正文（同步阻塞）。 */
    private String generateContent(String title, String topic, List<String> tags) {
        String prompt = draftTemplate
                .replace("{title}", nullToEmpty(title))
                .replace("{topic}", nullToEmpty(topic))
                .replace("{tags}", tags == null || tags.isEmpty() ? "" : String.join("、", tags));
        String content = chatClient.prompt()
                .user(prompt)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.5)
                        .maxTokens(properties.getPost().getDraftMaxTokens())
                        .build())
                .call()
                .content();
        return content == null ? "" : content.trim();
    }

    private String safeDescription(String content) {
        try {
            String d = descriptionService.generateDescription(content);
            return d == null ? "" : d;
        } catch (Exception e) {
            log.warn("generate description failed: {}", e.getMessage());
            return "";
        }
    }

    /** tags 转 know_posts.tags 列存储的 JSON 字符串（与 KnowPostServiceImpl.toJsonOrNull 一致）。 */
    private String tagsToJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String readClasspath(String path) throws IOException {
        try (var is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 草稿摘要（回传给 QA 工具，再以 SSE draft 事件下发前端）。 */
    public record DraftResult(String postId, String title, List<String> tags, String preview) {}
}
