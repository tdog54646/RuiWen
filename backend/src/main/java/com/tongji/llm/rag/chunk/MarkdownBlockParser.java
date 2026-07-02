package com.tongji.llm.rag.chunk;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 块级元素解析器（模式 B：纯规则，无需 LLM）。
 * <p>
 * 对标 RAGFlow 的 {@code MarkdownElementExtractor}，分两阶段处理：
 * <ol>
 *   <li><b>表格预提取</b>：用正则从文本中提取 Markdown 表格（含 HTML 表格），
 *      替换为占位符，保留表格原文供后续识别。</li>
 *   <li><b>块级元素逐行解析</b>：按优先级识别各类块（代码块 &gt; 列表 &gt; 引用
 *      &gt; 分隔线 &gt; 标题 &gt; 文本），输出 {@link MarkdownSection} 列表。</li>
 * </ol>
 * <p>
 * 核心设计：<b>Header Anchoring</b>——标题与紧随其下的非空内容行合并，
 * 确保标题不会被孤立，也不与无关内容混合。
 *
 * @see MarkdownSection
 * @see MarkdownSectionMerger
 */
@Slf4j
public class MarkdownBlockParser {

    // ─────────────────────────────────────────────────────────────────────────
    // 正则定义
    // ─────────────────────────────────────────────────────────────────────────

    /** Markdown 带边框表格（如 | A | B |） */
    private static final Pattern BORDER_TABLE = Pattern.compile(
            "(?:\\n|^)(\\|[^|]*\\|[^|]*\\|[^|]*\\n)(?:\\|(?:\\s*[:-]+[-| :]*\\s*)\\|[^|]*\\|[^|]*\\n)(?:\\|.*?\\|[^|]*\\|[^|]*\\n)+",
            Pattern.MULTILINE
    );

    /** Markdown 无边框表格（无首行 `|` 包裹） */
    private static final Pattern NO_BORDER_TABLE = Pattern.compile(
            "(?:\\n|^)(\\S[^|\n]*\\|[^|\n]*\\n)(?:\\s*[:-]+[-| :]*[^|\n]*\\n)((?:\\S[^|\n]*\\|[^|\n]*\\n)+)",
            Pattern.MULTILINE
    );

    /** HTML 表格 */
    private static final Pattern HTML_TABLE = Pattern.compile(
            "(?:\\n|^)\\s*<table[^>]*>.*?</table>",
            Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /** 分隔线：--- / *** / ___ */
    private static final Pattern DIVIDER = Pattern.compile("^\\s*([-*_]){3,}\\s*$");

    /** 行内代码：非 ``` 包裹的单反引号 */
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]+`");

    /**
     * 标题行。
     * 匹配 # ## ### #### ##### ###### 开头的内容。
     * group(1) = 井号数量（level）
     * group(2) = 标题文本
     */
    private static final Pattern HEADER = Pattern.compile("^(#{1,6})\\s+(.+)$");

    /**
     * 有序列表：1. 2. 10. 等开头（注意转义句点）。
     */
    private static final Pattern ORDERED_LIST = Pattern.compile("^\\s*\\d+\\.\\s+");

    /**
     * 无序列表：- / * / + 开头（后跟空格）。
     * 不匹配分隔线（--- 等会被 DIVIDER 优先捕获）。
     */
    private static final Pattern UNORDERED_LIST = Pattern.compile("^\\s*[-*+]\\s+");

    /**
     * 引用块：> 开头。
     */
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>\s?");

    // ─────────────────────────────────────────────────────────────────────────
    // 解析入口
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 将 Markdown 文本解析为结构化的 Section 列表。
     *
     * @param text 归一化后的 Markdown 原文
     * @return 按原文顺序排列的 MarkdownSection 列表（不含空 Section）
     */
    public List<MarkdownSection> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 统一 \r\n → \n
        String normalized = text.replaceAll("\\r\\n", "\n");
        String[] lines = normalized.split("\n", -1);

        List<MarkdownSection> sections = new ArrayList<>();
        int idx = 0;

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            // 跳过完全空白的行
            if (trimmed.isEmpty()) {
                i++;
                continue;
            }

            MarkdownSection section;
            int startLine = i + 1; // 行号从 1 开始

            if (isCodeBlockStart(trimmed, lines, i)) {
                // ── 代码块 ──
                section = extractCodeBlock(lines, i, idx++, startLine);
                i = section.endLine() + 1;

            } else if (DIVIDER.matcher(trimmed).find()) {
                // ── 分隔线 ──
                section = new MarkdownSection(idx++, BlockType.DIVIDER, 0,
                        trimmed, startLine, startLine);
                i++;

            } else if (HEADER.matcher(trimmed).find()) {
                // ── 标题（含 Header Anchoring）──
                section = extractHeaderWithFollowing(lines, i, idx++, startLine);
                i = section.endLine() + 1;

            } else if (isListItem(trimmed)) {
                // ── 列表块 ──
                section = extractListBlock(lines, i, idx++, startLine);
                i = section.endLine() + 1;

            } else if (BLOCKQUOTE.matcher(trimmed).find()) {
                // ── 引用块 ──
                section = extractBlockquote(lines, i, idx++, startLine);
                i = section.endLine() + 1;

            } else {
                // ── 正文段落 ──
                section = extractTextBlock(lines, i, idx++, startLine);
                i = section.endLine() + 1;
            }

            sections.add(section);
        }

        return sections;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 块级元素提取方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 判断行是否为代码块开始（三个反引号开头）。
     */
    private boolean isCodeBlockStart(String trimmed, String[] lines, int idx) {
        return trimmed.startsWith("```");
    }

    /**
     * 提取代码块：从 ```{language} 开始到下一个 ``` 结束。
     */
    private MarkdownSection extractCodeBlock(String[] lines, int startIdx, int idx, int startLine) {
        StringBuilder content = new StringBuilder();
        int endLine = startLine;

        // 包含开头的 ```{language}
        content.append(lines[startIdx]);

        int i = startIdx + 1;
        while (i < lines.length) {
            String l = lines[i];
            endLine++;
            content.append("\n").append(l);
            if (l.trim().startsWith("```")) {
                break;
            }
            i++;
        }

        return new MarkdownSection(idx, BlockType.CODE_BLOCK, 0,
                content.toString(), startLine, endLine);
    }

    /**
     * Header Anchoring：提取标题及其紧随内容。
     * <p>
     * 核心逻辑：
     * <ul>
     *   <li>标题行解析 level（# 数量）。</li>
     *   <li>查找紧随标题的第一个非空内容行。</li>
     *   <li>若存在内容行，与标题合并，确定合并后类型（HEADER_WITH_TEXT/CODE/LIST/BLOCKQUOTE）。</li>
     *   <li>若不存在内容行，标题单独成块（HEADER）。</li>
     * </ul>
     */
    private MarkdownSection extractHeaderWithFollowing(String[] lines, int headerIdx,
                                                       int idx, int startLine) {
        Matcher m = HEADER.matcher(lines[headerIdx].trim());
        if (!m.find()) {
            // 不匹配标题，返回为普通文本
            return extractTextBlock(lines, headerIdx, idx, startLine);
        }

        int level = m.group(1).length();
        StringBuilder content = new StringBuilder(lines[headerIdx]);

        int i = headerIdx + 1;
        while (i < lines.length) {
            String next = lines[i].trim();

            if (next.isEmpty()) {
                // 空行跳过，继续找
                i++;
                continue;
            }

            BlockType nextType = classifyLine(next);

            if (nextType == BlockType.CODE_BLOCK) {
                // 代码块整体合并
                MarkdownSection code = extractCodeBlock(lines, i, -1, i + 1);
                content.append("\n\n").append(code.content());
                BlockType mergedType = BlockType.HEADER_WITH_CODE;
                return new MarkdownSection(idx, mergedType, level,
                        content.toString(), startLine, code.endLine());

            } else if (nextType == BlockType.LIST) {
                MarkdownSection list = extractListBlock(lines, i, -1, i + 1);
                content.append("\n\n").append(list.content());
                return new MarkdownSection(idx, BlockType.HEADER_WITH_LIST, level,
                        content.toString(), startLine, list.endLine());

            } else if (nextType == BlockType.BLOCKQUOTE) {
                MarkdownSection quote = extractBlockquote(lines, i, -1, i + 1);
                content.append("\n\n").append(quote.content());
                return new MarkdownSection(idx, BlockType.HEADER_WITH_BLOCKQUOTE, level,
                        content.toString(), startLine, quote.endLine());

            } else if (nextType == BlockType.TABLE) {
                // 表格单独成块（标题不合并）
                // 这里把表格追加到标题后
                content.append("\n\n").append(next);
                return new MarkdownSection(idx, BlockType.HEADER_WITH_TABLE, level,
                        content.toString(), startLine, i + 1);

            } else {
                // 普通文本段落
                MarkdownSection text = extractTextBlock(lines, i, -1, i + 1);
                content.append("\n\n").append(text.content());
                return new MarkdownSection(idx, BlockType.HEADER_WITH_TEXT, level,
                        content.toString(), startLine, text.endLine());
            }
        }

        // 标题后没有内容行，单独成块
        return new MarkdownSection(idx, BlockType.HEADER, level,
                content.toString(), startLine, startLine);
    }

    /**
     * 提取列表块（无序 + 有序），包含所有续行和子列表。
     */
    private MarkdownSection extractListBlock(String[] lines, int startIdx, int idx, int startLine) {
        StringBuilder content = new StringBuilder();
        int endLine = startLine;
        boolean inList = true;

        int i = startIdx;
        while (i < lines.length) {
            String l = lines[i];
            String trimmed = l.trim();

            if (trimmed.isEmpty()) {
                // 列表项之间的空行（不打断列表）
                content.append("\n");
                endLine = i + 1;
                i++;
                continue;
            }

            if (isListItem(trimmed)) {
                if (content.length() > 0) {
                    content.append("\n");
                }
                content.append(l);
                endLine = i + 1;
                i++;
            } else if (l.startsWith(" ") || l.startsWith("\t")) {
                // 缩进续行（属于列表项的一部分）
                content.append("\n").append(l);
                endLine = i + 1;
                i++;
            } else if (inList && (isCodeBlockStart(trimmed, lines, i)
                    || BLOCKQUOTE.matcher(trimmed).find())) {
                // 列表内的嵌套块（如列表中的代码块）
                content.append("\n").append(l);
                endLine = i + 1;
                i++;
            } else {
                // 遇到非列表内容，退出
                break;
            }
        }

        return new MarkdownSection(idx, BlockType.LIST, 0,
                content.toString().trim(), startLine, endLine);
    }

    /**
     * 提取引用块（> 开头），包含所有缩进续行。
     */
    private MarkdownSection extractBlockquote(String[] lines, int startIdx, int idx, int startLine) {
        StringBuilder content = new StringBuilder();
        int endLine = startLine;

        int i = startIdx;
        while (i < lines.length) {
            String l = lines[i];
            String trimmed = l.trim();

            if (BLOCKQUOTE.matcher(trimmed).find()) {
                if (content.length() > 0) {
                    content.append("\n");
                }
                content.append(l);
                endLine = i + 1;
                i++;
            } else if (l.startsWith(" ") || l.startsWith("\t")) {
                // 引用块的续行（可能缩进）
                content.append("\n").append(l);
                endLine = i + 1;
                i++;
            } else {
                break;
            }
        }

        return new MarkdownSection(idx, BlockType.BLOCKQUOTE, 0,
                content.toString().trim(), startLine, endLine);
    }

    /**
     * 提取普通正文段落（遇到空行或非文本块为止）。
     */
    private MarkdownSection extractTextBlock(String[] lines, int startIdx, int idx, int startLine) {
        StringBuilder content = new StringBuilder();
        int endLine = startLine;

        int i = startIdx;
        while (i < lines.length) {
            String l = lines[i];
            String trimmed = l.trim();

            if (trimmed.isEmpty()) {
                // 空行作为段落分隔
                if (content.length() > 0) {
                    endLine = i + 1;
                    break;
                }
                i++;
                continue;
            }

            // 遇到块级元素则退出
            if (isCodeBlockStart(trimmed, lines, i)
                    || DIVIDER.matcher(trimmed).find()
                    || HEADER.matcher(trimmed).find()
                    || isListItem(trimmed)
                    || BLOCKQUOTE.matcher(trimmed).find()) {
                break;
            }

            if (content.length() > 0) {
                content.append("\n");
            }
            content.append(l);
            endLine = i + 1;
            i++;
        }

        return new MarkdownSection(idx, BlockType.TEXT, 0,
                content.toString().trim(), startLine, endLine);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 判断行是否为列表项。
     */
    private boolean isListItem(String trimmed) {
        return ORDERED_LIST.matcher(trimmed).find()
                || UNORDERED_LIST.matcher(trimmed).find();
    }

    /**
     * 对单行做类型分类（用于 Header Anchoring 的内容行判断）。
     * 不做跨行聚合，仅返回该行的类型。
     */
    private BlockType classifyLine(String trimmed) {
        if (trimmed.startsWith("```")) {
            return BlockType.CODE_BLOCK;
        }
        if (isListItem(trimmed)) {
            return BlockType.LIST;
        }
        if (BLOCKQUOTE.matcher(trimmed).find()) {
            return BlockType.BLOCKQUOTE;
        }
        if (DIVIDER.matcher(trimmed).find()) {
            return BlockType.DIVIDER;
        }
        if (HEADER.matcher(trimmed).find()) {
            return BlockType.HEADER;
        }
        return BlockType.TEXT;
    }
}
