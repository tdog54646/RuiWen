package com.tongji.llm.rag.chunk;

import com.tongji.llm.rag.model.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 语义文本分块器（Semantic Chunker）。
 * <p>
 * 支持两种切分策略，由配置项 {@code rag.chunk.mode} 决定：
 * <ul>
 *   <li><b>rule（默认，推荐）</b>：纯规则模式，使用 {@link MarkdownBlockParser} 解析
 *       Markdown 块级元素（标题/代码/表格/列表等），再由 {@link MarkdownSectionMerger}
 *       按 token 限制合并，零成本、无延迟。</li>
 *   <li><b>llm</b>：LLM 增强模式，调用 DeepSeek 完成结构提取和合并，
 *       效果更好但有 API 调用开销。</li>
 *   <li><b>legacy（默认关闭）</b>：原有逻辑，使用段落边界 + TokenTextSplitter，
 *       通过设置 {@code rag.chunk.markdown-aware: false} 启用。</li>
 * </ul>
 *
 * @see MarkdownBlockParser
 * @see MarkdownSectionMerger
 * @see LlmMarkdownChunker
 * @see RagProperties.Chunk
 */
@Slf4j
@Component
public class SemanticChunker {

    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("(\\r\\n){2,}|\\n{2,}");
    private static final Pattern CHINESE_PUNCT = Pattern.compile("[。！？；]");

    private final TokenTextSplitter tokenSplitter;
    private final RagProperties properties;

    // 模式 B 组件（按需初始化）
    private MarkdownBlockParser blockParser;
    private MarkdownSectionMerger sectionMerger;

    // 模式 A 组件（按需注入）
    private final LlmMarkdownChunker llmChunker;

    public SemanticChunker(RagProperties properties, LlmMarkdownChunker llmChunker) {
        this.properties = properties;
        this.llmChunker = llmChunker;
        this.tokenSplitter = new TokenTextSplitter(
                properties.getChunk().getSize(),
                properties.getChunk().getOverlap(),
                1,
                Integer.MAX_VALUE,
                true
        );
    }

    /**
     * 对输入文本进行语义分块，返回 chunk 列表。
     * <p>
     * 路由逻辑：
     * <ol>
     *   <li>{@code markdown-aware=false} → 原有 TokenTextSplitter 逻辑</li>
     *   <li>{@code mode=llm} → LLM 增强切分</li>
     *   <li>{@code mode=rule} 或默认 → 纯规则切分</li>
     * </ol>
     *
     * @param text 待分块的原始文本（通常为 Markdown 纯文本）
     * @return 分块后的文本列表，顺序保持原文顺序
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 短文本快捷路径
        int estimatedTokens = estimateTokens(text);
        if (estimatedTokens < 50) {
            return List.of(text);
        }

        // 路由
        if (!properties.getChunk().isMarkdownAware()) {
            return chunkLegacy(text);
        }

        return switch (properties.getChunk().getMode()) {
            case LLM -> chunkByLLM(text);
            default  -> chunkByRule(text);  // null / unknown → default to rule
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 模式 A：纯规则切分
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> chunkByRule(String text) {
        // 初始化（延迟加载，避免构造函数中直接注入未知组件）
        if (blockParser == null) {
            blockParser = new MarkdownBlockParser();
            sectionMerger = new MarkdownSectionMerger(
                    properties.getChunk().getSize(),
                    properties.getChunk().getOverlapPercent(),
                    properties.getChunk().getMinLength()
            );
        }

        // 文本归一化
        String normalized = normalize(text);

        // Step 1: 解析块级元素
        List<MarkdownSection> sections = blockParser.parse(normalized);
        if (sections.isEmpty()) {
            // 解析失败，降级到 legacy
            log.debug("Block parser returned empty, falling back to legacy");
            return chunkLegacy(text);
        }

        // Step 2: Section → Chunk 合并
        List<MarkdownChunkResult> chunks = sectionMerger.merge(sections);

        // Step 3: 提取 content 字段
        List<String> result = chunks.stream()
                .map(MarkdownChunkResult::content)
                .toList();

        log.debug("Rule chunking: sections={}, chunks={}", sections.size(), result.size());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 模式 B：LLM 增强切分
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> chunkByLLM(String text) {
        List<String> chunks = llmChunker.chunk(text);
        if (chunks.isEmpty()) {
            // LLM 失败，降级到 rule 模式
            log.warn("LLM chunking returned empty, falling back to rule mode");
            return chunkByRule(text);
        }
        return chunks;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 原有逻辑（legacy / markdown-aware=false）
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> chunkLegacy(String text) {
        String normalized = normalize(text);

        // Step 1: 段落边界 + 中文标点切分
        List<String> semanticUnits = splitBySemanticBoundaries(normalized);

        // Step 2: join 后送入 TokenTextSplitter
        String prepared = String.join("\n\n", semanticUnits);
        Document doc = new Document(prepared);
        List<Document> splitDocs = tokenSplitter.split(doc);

        // Step 3: 长度过滤
        int minLen = properties.getChunk().getMinLength();
        int maxLen = properties.getChunk().getMaxLength();
        List<String> result = new ArrayList<>(splitDocs.size());
        for (Document splitDoc : splitDocs) {
            String chunk = splitDoc.getText();
            int len = chunk.codePointCount(0, chunk.length());
            if (len >= minLen && len <= maxLen) {
                result.add(chunk);
            } else if (len > maxLen) {
                result.add(truncateByCodePoints(chunk, maxLen));
            }
            // len < minLen → 丢弃
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 原有辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> splitBySemanticBoundaries(String text) {
        List<String> units = new ArrayList<>();
        String[] paras = PARAGRAPH_SEPARATOR.split(text);

        for (String para : paras) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int approxTokens = estimateTokens(trimmed);
            if (approxTokens <= properties.getChunk().getSize() / 2) {
                units.add(trimmed);
                continue;
            }
            List<String> subUnits = splitByChinesePunctuation(trimmed);
            units.addAll(subUnits);
        }
        return units;
    }

    private List<String> splitByChinesePunctuation(String text) {
        List<String> result = new ArrayList<>();
        String[] sentences = CHINESE_PUNCT.split(text);
        StringBuilder buffer = new StringBuilder();

        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (buffer.isEmpty()) {
                buffer.append(s);
            } else {
                int bufTokens = estimateTokens(buffer.toString());
                int sentTokens = estimateTokens(s);
                if (bufTokens + sentTokens <= properties.getChunk().getSize() / 2) {
                    buffer.append("。").append(s);
                } else {
                    result.add(buffer.toString());
                    buffer.setLength(0);
                    buffer.append(s);
                }
            }
        }
        if (!buffer.isEmpty()) {
            result.add(buffer.toString());
        }
        return result.isEmpty() ? List.of(text) : result;
    }

    private String normalize(String text) {
        return text
                .replaceAll("\\r\\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n[ \\t]+", "\n")
                .trim();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long wordCount = text.chars().filter(c -> c == ' ' || c == '\t').count();
        return (int) (text.codePointCount(0, text.length()) + wordCount * 0.3 + 1);
    }

    private String truncateByCodePoints(String text, int limit) {
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (count >= limit) break;
            sb.appendCodePoint(cp);
            count++;
            i += Character.charCount(cp);
        }
        return sb.toString();
    }
}
