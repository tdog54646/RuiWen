package com.tongji.llm.rag.chunk;

/**
 * 解析出的单个 Section（对应 RAGFlow 的 sections）。
 * <p>
 * 每个 Section 是一个独立的语义单元，可能是：
 * <ul>
 *   <li>纯标题（HEADER）</li>
 *   <li>标题 + 紧随内容（HEADER_WITH_*）</li>
 *   <li>代码块、列表、表格、引用等独立块</li>
 *   <li>普通正文段落（TEXT）</li>
 * </ul>
 *
 * @param index     Section 在原文中的顺序索引（从 0 开始）
 * @param type      块级元素类型
 * @param level     标题层级（仅 HEADER* 类型有效，范围 1~6）
 * @param content   原始 Markdown 内容（含语法符号）
 * @param startLine 起始行号（从 1 开始，含）
 * @param endLine   结束行号（含）
 */
public record MarkdownSection(
        int index,
        BlockType type,
        int level,
        String content,
        int startLine,
        int endLine
) {
    public MarkdownSection {
        if (level < 0 || level > 6) {
            level = 0;
        }
    }

    /** 判断是否为纯标题（无紧随内容） */
    public boolean isBareHeader() {
        return type == BlockType.HEADER;
    }

    /** 判断是否为分隔线 */
    public boolean isDivider() {
        return type == BlockType.DIVIDER;
    }

    /** 估算 content 的 token 数（中文字符×1 + 英文单词数×1.3） */
    public int estimateTokens() {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        long chinese = content.chars().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN).count();
        long words = content.chars().filter(c -> c == ' ' || c == '\t' || c == '\n').count();
        return (int) (chinese + words * 1.3 + 1);
    }
}
