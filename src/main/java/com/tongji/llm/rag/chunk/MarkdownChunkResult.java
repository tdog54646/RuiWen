package com.tongji.llm.rag.chunk;

import java.util.Collections;
import java.util.List;

/**
 * 合并后的最终 Chunk。
 * <p>
 * 对应 ES 索引中一条文档的 content 字段以及 metadata 扩展字段。
 *
 * @param index               Chunk 在最终列表中的顺序（从 0 开始，重编后连续）
 * @param content             合并后的完整 Markdown 文本（含语法符号）
 * @param sourceSectionTypes  来源 Section 的类型列表，用于溯源（如 ["header_with_text", "code_block"]）
 * @param sourceLineRange     来源行号范围，如 "1-10"
 * @param estTokens           估算 token 数
 * @param children            子 Chunk 列表（当配置了 children-delimiters 且 Chunk 内部有分隔符时产生）
 */
public record MarkdownChunkResult(
        int index,
        String content,
        List<String> sourceSectionTypes,
        String sourceLineRange,
        int estTokens,
        List<SubChunk> children
) {
    public MarkdownChunkResult {
        if (sourceSectionTypes == null) {
            sourceSectionTypes = Collections.emptyList();
        }
        if (children == null) {
            children = Collections.emptyList();
        }
    }

    /**
     * 简化构造器：不含子 Chunk 时使用。
     */
    public MarkdownChunkResult(int index, String content, List<String> sourceSectionTypes,
                               String sourceLineRange, int estTokens) {
        this(index, content, sourceSectionTypes, sourceLineRange, estTokens, Collections.emptyList());
    }

    /**
     * 估算 content 的 token 数。
     */
    public int estimateTokens() {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        long chinese = content.chars().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN).count();
        long words = content.chars().filter(c -> c == ' ' || c == '\t' || c == '\n').count();
        return (int) (chinese + words * 1.3 + 1);
    }

    /**
     * 子 Chunk 描述。
     *
     * @param content   子 Chunk 内容
     * @param delimiter 触发拆分的分隔符（如 "---" 或 "##"），null 表示末尾
     */
    public record SubChunk(String content, String delimiter) {}
}
