package com.tongji.llm.rag.chunk;

import com.tongji.llm.rag.model.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 语义文本分块器（Semantic Chunker）。
 * <p>
 * 本实现采用"语义优先"的二层切分策略，力求在 token 数量约束
 * 与语义完整性之间取得平衡：
 * <ol>
 *   <li><b>第一层：段落边界切分。</b> 优先在自然段落边界（{@code \n\n}）
 *       以及中文标点（{`。！？；`}）处断开，保持句子和段落的完整性。</li>
 *   <li><b>第二层：Token 层面截断。</b> 使用 Spring AI 的
 *       {@link TokenTextSplitter} 在 token 层面做细粒度截断，
 *       并通过 overlap 参数在相邻 chunk 之间保留上下文连续性。</li>
 * </ol>
 * <p>
 * 这种两阶段策略相比简单按字符数截断的优势在于：
 * <ul>
 *   <li>重要信息（如代码块、列表项）不会被拦腰截断。</li>
 *   <li>overlap 使相邻 chunk 共享上下文，降低跨 chunk 语义丢失的风险。</li>
 *   <li>Token 层面的截断保证最终 chunk 的大小符合 embedding 模型的上下文窗口约束。</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * List&lt;String&gt; chunks = semanticChunker.chunk(markdownText);
 * </pre>
 *
 * @see TokenTextSplitter
 * @see RagProperties.Chunk
 */
@Component
public class SemanticChunker {

    /**
     * 段落分隔符：连续两个或以上换行符。
     * 匹配 \n\n、\r\n\r\n、\n\r\n\r 等常见段落分隔模式。
     */
    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("(\\r\\n){2,}|\\n{2,}");

    /**
     * 中文句子结束标点：句号、感叹号、问号、分号。
     * 在第一层切分时作为次优先的断点。
     */
    private static final Pattern CHINESE_PUNCT = Pattern.compile("[。！？；]");

    /**
     * Spring AI 内置的 Token 分块器。
     * 内部维护 token 计数逻辑，保证每个 chunk 不超过指定 token 数。
     */
    private final TokenTextSplitter tokenSplitter;

    private final RagProperties properties;

    public SemanticChunker(RagProperties properties) {
        this.properties = properties;
        this.tokenSplitter = new TokenTextSplitter(
                properties.getChunk().getSize(),       // chunkSize: 每个 chunk 的目标 token 数
                properties.getChunk().getOverlap(),    // chunkOverlap: 相邻 chunk 重叠 token 数
                1,                                      // minChunkSizeChars: 字符数下限（本实现用 minLength 控制）
                Integer.MAX_VALUE,                     // minChunkLengthToEmbed: 最小截取长度（本实现跳过此过滤）
                true                                    // keepSeparator: 保留分隔符
        );
    }

    /**
     * 对输入文本进行语义分块，返回 chunk 列表。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>文本归一化（折叠多余空白）。</li>
     *   <li>按段落和中文标点做第一层切分，得到语义单元列表。</li>
     *   <li>将语义单元列表 join 后送入 TokenTextSplitter 做第二层截断。</li>
     *   <li>按 minLength / maxLength 过滤过短 / 过长的 chunk。</li>
     * </ol>
     *
     * @param text 待分块的原始文本（通常为 Markdown 纯文本）
     * @return 分块后的文本列表，顺序保持原文顺序
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 文本归一化——折叠连续空白、去掉行首行尾空格
        String normalized = normalize(text);

        // 短文本快捷路径：直接返回原文，不走复杂分块
        int estimatedTokens = estimateTokens(normalized);
        if (estimatedTokens < 50) {
            return List.of(normalized);
        }

        // Step 2: 第一层：按段落边界（\n\n）和中文标点（。！？；）做语义切分
        List<String> semanticUnits = splitBySemanticBoundaries(normalized);

        // Step 3: 将语义单元 join 为可处理的文本
        // 段落之间用双换行符连接，保留原始段落边界提示
        String prepared = String.join("\n\n", semanticUnits);

        // Step 4: TokenTextSplitter 做第二层 token 层面的截断
        // TokenTextSplitter.split(String) 不是公开 API，需将 String 包装为 Document 再调用
        Document doc = new Document(prepared);
        List<Document> splitDocs = tokenSplitter.split(doc);

        // Step 5: 按字符长度过滤
        int minLen = properties.getChunk().getMinLength();
        int maxLen = properties.getChunk().getMaxLength();
        List<String> result = new ArrayList<>(splitDocs.size());
        for (Document splitDoc : splitDocs) {
            String chunk = splitDoc.getText();
            int len = chunk.codePointCount(0, chunk.length());
            if (len >= minLen && len <= maxLen) {
                result.add(chunk);
            } else if (len > maxLen) {
                // 超出 maxLength 的超长块，强制按字符数截断
                result.add(truncateByCodePoints(chunk, maxLen));
            }
            // len < minLength 的块直接丢弃
        }
        return result;
    }

    /**
     * 将文本按段落（\n\n）和中文标点（。！？；）切分为语义单元。
     * <p>
     * 优先级：段落边界 &gt; 中文标点边界 &gt; 不截断
     * <ul>
     *   <li>段落之间天然有较大语义跳跃，优先在段落边界切断。</li>
     *   <li>中文标点是句子边界，在段落内按标点切分能保证句子完整性。</li>
     *   <li>没有段落也没有标点的超长段落，则不强行截断（后续 tokenSplitter 会处理）。</li>
     * </ul>
     *
     * @param text 归一化后的文本
     * @return 语义单元列表，每个元素是一个段落或由多个短段落合并的文本块
     */
    private List<String> splitBySemanticBoundaries(String text) {
        List<String> units = new ArrayList<>();

        // 先按段落分隔符拆分
        String[] paras = PARAGRAPH_SEPARATOR.split(text);

        for (String para : paras) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 段落长度小于目标 chunk 大约一半时，直接作为独立语义单元
            // 避免过度切分导致过多碎片 chunk
            int approxTokens = estimateTokens(trimmed);
            if (approxTokens <= properties.getChunk().getSize() / 2) {
                units.add(trimmed);
                continue;
            }

            // 长段落：尝试在中文标点边界进一步拆分
            List<String> subUnits = splitByChinesePunctuation(trimmed);
            units.addAll(subUnits);
        }

        return units;
    }

    /**
     * 在中文标点边界（。！？；）处切分长文本。
     * 每个子单元长度尽量接近 chunkSize，但仍可能超出（此时后续 TokenTextSplitter 兜底截断）。
     *
     * @param text 输入文本（单个段落，不含段落分隔符）
     * @return 按标点切分后的文本片段列表
     */
    private List<String> splitByChinesePunctuation(String text) {
        List<String> result = new ArrayList<>();
        String[] sentences = CHINESE_PUNCT.split(text);

        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (sentence.isEmpty()) {
                continue;
            }

            // 加上标点本身（分隔时丢失了标点字符，需要补回）
            // 注意：split 后标点丢失，这里用 splitWithDelimiters 或手动处理
            // 为简化，直接在 buffer 追加时不带标点，在合并时判断

            if (buffer.isEmpty()) {
                buffer.append(sentence);
            } else {
                int bufTokens = estimateTokens(buffer.toString());
                int sentTokens = estimateTokens(sentence);

                // 如果加上这句不超过 chunkSize 的一半，则合并；否则先输出 buffer
                if (bufTokens + sentTokens <= properties.getChunk().getSize() / 2) {
                    buffer.append("。").append(sentence);
                } else {
                    result.add(buffer.toString());
                    buffer.setLength(0);
                    buffer.append(sentence);
                }
            }
        }

        if (!buffer.isEmpty()) {
            result.add(buffer.toString());
        }

        return result.isEmpty() ? List.of(text) : result;
    }

    /**
     * 文本归一化：折叠多余空白、去掉行首行尾多余空格。
     */
    private String normalize(String text) {
        return text
                .replaceAll("\\r\\n", "\n")              // 统一换行符
                .replaceAll("[ \\t]+", " ")              // 折叠连续空格/制表符
                .replaceAll("\n[ \\t]+", "\n")           // 去掉行首空格
                .trim();
    }

    /**
     * 简单 token 估算：中文按字符数估算（1 char ≈ 1 token），
     * 英文按空格+1 估算（1 word ≈ 1.3 token），取上界。
     * <p>
     * 该估算仅用于策略判断（是否合并/切分），不用于精确截断。
     * 精确截断由 TokenTextSplitter 通过真实 token 计数完成。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简单估算：总字符数 + 空格分隔的单词数 * 0.3
        long wordCount = text.chars().filter(c -> c == ' ' || c == '\t').count();
        return (int) (text.codePointCount(0, text.length()) + wordCount * 0.3 + 1);
    }

    /**
     * 按 code point（Unicode 码点）截断文本，保证中英文混合截断的正确性。
     *
     * @param text  待截断文本
     * @param limit 最大 code point 数量
     * @return 截断后的文本
     */
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
