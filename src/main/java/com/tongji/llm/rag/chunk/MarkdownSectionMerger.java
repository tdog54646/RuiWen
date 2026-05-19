package com.tongji.llm.rag.chunk;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Section → Chunk 合并器。
 * <p>
 * 对标 RAGFlow 的 Section Merger，按 token 限制将多个 {@link MarkdownSection} 合并为
 * {@link MarkdownChunkResult}。
 * <p>
 * 合并算法约束：
 * <ul>
 *   <li>Section 为最小合并单元，不跨 Section 切分。</li>
 *   <li>代码块、表格等不可拆分，即使单个 Section 超 token 限制也强制作为独立 Chunk。</li>
 *   <li>支持 overlap 百分比重叠：相邻 Chunk 末尾重叠一定比例的内容。</li>
 *   <li>过滤 divider 类型（不输出到最终结果）。</li>
 *   <li>过滤 token 数低于 minChunkTokens 的碎片。</li>
 *   <li>position 重编：过滤后重新从 0 开始编号，保证连续。</li>
 * </ul>
 *
 * @see MarkdownBlockParser
 * @see MarkdownChunkResult
 */
@Slf4j
public class MarkdownSectionMerger {

    private final int chunkTokenLimit;
    private final int overlapPercent;
    private final int minChunkTokens;

    public MarkdownSectionMerger(int chunkTokenLimit, int overlapPercent, int minChunkTokens) {
        this.chunkTokenLimit = chunkTokenLimit;
        this.overlapPercent = overlapPercent;
        this.minChunkTokens = minChunkTokens;
    }

    /**
     * 将 Section 列表合并为 Chunk 列表。
     *
     * @param sections MarkdownBlockParser 输出的 Section 列表
     * @return 合并后的 Chunk 列表（不含 divider，且 position 连续）
     */
    public List<MarkdownChunkResult> merge(List<MarkdownSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }

        List<MarkdownSection> filtered = new ArrayList<>();
        for (MarkdownSection sec : sections) {
            // 过滤 divider 类型
            if (sec.isDivider()) {
                continue;
            }
            // 过滤空内容
            if (sec.content() == null || sec.content().isBlank()) {
                continue;
            }
            filtered.add(sec);
        }

        List<MarkdownChunkResult> rawChunks = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        List<String> currentTypes = new ArrayList<>();
        List<Integer> currentSourceIndices = new ArrayList<>();
        int currentTokens = 0;
        int startLine = 0;
        int endLine = 0;

        for (MarkdownSection sec : filtered) {
            int secTokens = sec.estimateTokens();

            // Case 1: 单个 Section 超限 → 强制作为独立 Chunk
            if (secTokens > chunkTokenLimit) {
                // 先输出当前累积的 chunk
                flushCurrent(rawChunks, currentContent, currentTypes,
                        currentSourceIndices, currentTokens, startLine, endLine);

                // 强制将此 Section 作为独立 Chunk（不拆分）
                String content = sec.content();
                // 超长标记，便于调试
                String marked = content;
                rawChunks.add(new MarkdownChunkResult(
                        rawChunks.size(),   // 暂用 size，后续会重编
                        marked,
                        List.of(sec.type().name().toLowerCase()),
                        sec.startLine() + "-" + sec.endLine(),
                        secTokens
                ));

                // 重置状态
                currentContent.setLength(0);
                currentTypes.clear();
                currentSourceIndices.clear();
                currentTokens = 0;
                startLine = 0;
                endLine = 0;
                continue;
            }

            // Case 2: 加上此 Section 会超限 → 先输出当前 chunk
            if (currentTokens + secTokens > chunkTokenLimit) {
                flushCurrent(rawChunks, currentContent, currentTypes,
                        currentSourceIndices, currentTokens, startLine, endLine);

                // 重叠处理
                if (overlapPercent > 0 && currentContent.length() > 0) {
                    String prevContent = currentContent.toString();
                    int overlapLen = (int) (prevContent.length()
                            * overlapPercent / 100.0);
                    // 按字符数估算 overlap tokens
                    int overlapTokens = estimateTokens(
                            prevContent.substring(prevContent.length() - overlapLen));

                    String tail = prevContent.substring(prevContent.length() - overlapLen);
                    currentContent = new StringBuilder(tail)
                            .append("\n")
                            .append(sec.content());
                    currentTokens = overlapTokens + secTokens;
                } else {
                    currentContent = new StringBuilder(sec.content());
                    currentTokens = secTokens;
                }

                currentTypes = new ArrayList<>();
                currentTypes.add(sec.type().name().toLowerCase());
                currentSourceIndices = new ArrayList<>();
                currentSourceIndices.add(sec.index());
                startLine = sec.startLine();
                endLine = sec.endLine();
            }

            // Case 3: 累积到当前 chunk
            if (currentContent.isEmpty()) {
                currentContent.append(sec.content());
                startLine = sec.startLine();
                currentTokens = secTokens;
            } else {
                currentContent.append("\n").append(sec.content());
                currentTokens += secTokens;
            }
            currentTypes.add(sec.type().name().toLowerCase());
            currentSourceIndices.add(sec.index());
            endLine = sec.endLine();
        }

        // 最后一个 chunk
        flushCurrent(rawChunks, currentContent, currentTypes,
                currentSourceIndices, currentTokens, startLine, endLine);

        // 过滤过短 chunk
        List<MarkdownChunkResult> result = rawChunks.stream()
                .filter(c -> c.estimateTokens() >= minChunkTokens)
                .toList();

        // 重编 position（保证连续）
        List<MarkdownChunkResult> renumbered = new ArrayList<>(result.size());
        for (int i = 0; i < result.size(); i++) {
            MarkdownChunkResult c = result.get(i);
            renumbered.add(new MarkdownChunkResult(
                    i,
                    c.content(),
                    c.sourceSectionTypes(),
                    c.sourceLineRange(),
                    c.estTokens(),
                    c.children()
            ));
        }

        log.debug("Merged {} sections → {} chunks (tokenLimit={}, minTokens={})",
                filtered.size(), renumbered.size(), chunkTokenLimit, minChunkTokens);

        return renumbered;
    }

    /**
     * 将当前累积的 chunk 刷入结果列表。
     */
    private void flushCurrent(List<MarkdownChunkResult> chunks,
                              StringBuilder content,
                              List<String> types,
                              List<Integer> sourceIndices,
                              int tokens,
                              int startLine,
                              int endLine) {
        if (content.length() == 0) {
            return;
        }
        String lineRange = (startLine > 0 ? startLine : 1) + "-" + endLine;
        chunks.add(new MarkdownChunkResult(
                chunks.size(),
                content.toString(),
                new ArrayList<>(types),
                lineRange,
                tokens
        ));
    }

    /**
     * 估算字符串的 token 数。
     */
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
