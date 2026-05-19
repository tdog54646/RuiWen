package com.tongji.llm.rag.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tongji.llm.rag.model.RagProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM 增强的 Markdown 切分服务（模式 A）。
 * <p>
 * 一次性调用 DeepSeek 完成 Markdown 结构提取和 Chunk 合并，
 * 相比纯规则模式（MarkdownBlockParser），对复杂嵌套结构的识别更准确。
 * <p>
 * 流程：
 * <ol>
 *   <li>加载 one-shot 提示词模板，填入配置变量。</li>
 *   <li>调用 ChatClient 发送 Markdown 原文，获取 JSON 响应。</li>
 *   <li>解析 JSON，过滤低于 minLength 的碎片。</li>
 *   <li>重编 position，保证 0, 1, 2... 连续。</li>
 * </ol>
 *
 * @see MarkdownBlockParser（模式 B：纯规则）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmMarkdownChunker {

    private static final String PROMPT_FILE = "rag-prompts/markdown-chunk-one-shot-prompt.md";

    private final ChatClient chatClient;
    private final RagProperties properties;

    /**
     * 使用 LLM 对 Markdown 文本进行结构感知的切分。
     *
     * @param markdownText Markdown 原文
     * @return 切分后的文本列表（顺序与原文一致）
     */
    public List<String> chunk(String markdownText) {
        if (markdownText == null || markdownText.isBlank()) {
            return List.of();
        }

        int chunkTokenLimit = properties.getChunk().getSize();
        int overlapPercent = (int) (properties.getChunk().getOverlap() * 100.0
                / Math.max(1, properties.getChunk().getSize()));
        int minChunkTokens = properties.getChunk().getMinLength();

        // 加载提示词模板
        String promptTemplate;
        try {
            promptTemplate = loadPrompt(PROMPT_FILE);
        } catch (IOException e) {
            log.error("Failed to load LLM chunking prompt: {}", e.getMessage());
            // 降级：使用空 chunk 列表
            return List.of();
        }

        // 组装 system prompt（提示词模板内容）
        String systemPrompt = buildSystemPrompt(promptTemplate, chunkTokenLimit,
                overlapPercent, minChunkTokens);

        // 调用 LLM
        String jsonResponse;
        try {
            jsonResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(markdownText)
                    .advisors(new SimpleLoggerAdvisor())
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("LLM chunking failed, falling back to empty: {}", e.getMessage());
            return List.of();
        }

        if (jsonResponse == null || jsonResponse.isBlank()) {
            return List.of();
        }

        // 解析 JSON
        List<MarkdownChunkResult> chunks = parseChunks(jsonResponse);
        if (chunks.isEmpty()) {
            return List.of();
        }

        // 过滤过短 chunk 并重编 position
        List<String> result = new ArrayList<>();
        for (MarkdownChunkResult chunk : chunks) {
            if (chunk.estTokens() >= minChunkTokens) {
                result.add(chunk.content());
            }
        }

        log.info("LLM chunking: input length={}, output chunks={}",
                markdownText.length(), result.size());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 提示词加载
    // ─────────────────────────────────────────────────────────────────────────

    private String loadPrompt(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (var is = resource.getInputStream();
             var reader = new java.io.InputStreamReader(is, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            return sb.toString();
        }
    }

    private String buildSystemPrompt(String template, int chunkTokenLimit,
                                    int overlapPercent, int minChunkTokens) {
        // 简单模板替换：将占位符替换为实际值
        return template
                .replace("{{chunk_token_limit}}", String.valueOf(chunkTokenLimit))
                .replace("{{overlap_percent}}", String.valueOf(overlapPercent))
                .replace("{{min_chunk_tokens}}", String.valueOf(minChunkTokens))
                .replace("{{children_delimiters}}", "##,---,***");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON 解析
    // ─────────────────────────────────────────────────────────────────────────

    private List<MarkdownChunkResult> parseChunks(String json) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);

            // 支持直接返回数组 或 { "chunks": [...] } 结构
            var chunksNode = node.has("chunks") ? node.get("chunks") : node;
            if (!chunksNode.isArray()) {
                log.warn("Unexpected JSON structure from LLM, expected chunks array");
                return List.of();
            }

            List<MarkdownChunkResult> results = new ArrayList<>();
            for (var elem : chunksNode) {
                String content = elem.has("content") ? elem.get("content").asText() : "";
                int estTokens = elem.has("est_tokens")
                        ? elem.get("est_tokens").asInt()
                        : estimateTokens(content);
                String lineRange = elem.has("source_line_range")
                        ? elem.get("source_line_range").asText()
                        : "unknown";
                List<String> sectionTypes = new ArrayList<>();
                if (elem.has("source_section_types") && elem.get("source_section_types").isArray()) {
                    for (var t : elem.get("source_section_types")) {
                        sectionTypes.add(t.asText());
                    }
                }

                results.add(new MarkdownChunkResult(
                        results.size(),
                        content,
                        sectionTypes,
                        lineRange,
                        estTokens
                ));
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON response: {}", e.getMessage());
            return List.of();
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long chinese = text.chars()
                .filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)
                .count();
        long words = text.chars().filter(c -> c == ' ' || c == '\t' || c == '\n').count();
        return (int) (chinese + words * 1.3 + 1);
    }
}
