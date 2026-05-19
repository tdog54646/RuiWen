package com.tongji.llm.rag.chunk;

/**
 * Markdown 块级元素的类型枚举。
 * 与 markdown-extractor-prompt.md / markdown-chunk-one-shot-prompt.md 中的 type 字段对应。
 */
public enum BlockType {
    /** 纯标题（标题后无紧随内容） */
    HEADER,
    /** 标题 + 正文段落 */
    HEADER_WITH_TEXT,
    /** 标题 + 代码块 */
    HEADER_WITH_CODE,
    /** 标题 + 列表 */
    HEADER_WITH_LIST,
    /** 标题 + 引用块 */
    HEADER_WITH_BLOCKQUOTE,
    /** 标题 + 表格 */
    HEADER_WITH_TABLE,
    /** 代码块（三个反引号包裹） */
    CODE_BLOCK,
    /** 列表块（无序/有序） */
    LIST,
    /** 引用块（> 开头） */
    BLOCKQUOTE,
    /** 表格 */
    TABLE,
    /** 分隔线（--- / *** / ___） */
    DIVIDER,
    /** 普通正文段落 */
    TEXT,
    /** 无法识别的类型 */
    UNKNOWN
}
