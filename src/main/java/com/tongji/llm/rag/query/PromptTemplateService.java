package com.tongji.llm.rag.query;

import com.tongji.llm.rag.model.RagProperties;
import com.tongji.llm.rag.model.RetrievalChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG Prompt 模板构建服务。
 * <p>
 * 负责将 Top N 检索到的 Chunk 拼接为符合 LLM 输入规范的 Prompt，
 * 包括 System Prompt（角色设定与约束）和 User Prompt（上下文 + 问题）。
 *
 * <h2>Prompt 设计原则</h2>
 * <ol>
 *   <li><b>强制上下文约束。</b> System Prompt 明确要求 LLM"只能基于上下文作答"，
 *       不允许凭先验知识编造答案。</li>
 *   <li><b>防幻觉兜底。</b> 若上下文中没有相关信息，LLM 应如实告知，
 *       而不是返回一个看似合理但上下文未提及的答案。</li>
 *   <li><b>来源标注。</b> 每个 Chunk 前标注 [来源] 字段，便于 LLM 理解信息出处，
 *       也便于用户判断答案的可信度。</li>
 *   <li><b>简洁上下文。</b> Chunk 内容过短（低于 20 字符）时跳过，
 *       避免无意义碎片占用 Prompt token 限额。</li>
 *   <li><b>上下文截断。</b> 超过 {@code rag.prompt.context-limit} 个 Chunk 时截断，
 *       防止 Prompt 过长超过 LLM 的上下文窗口。</li>
 * </ol>
 *
 * @see RagProperties.Prompt
 * @see RetrievalChunk
 */
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final RagProperties properties;

    /**
     * 构建完整的 RAG 对话 Prompt（包含 System 和 User 部分）。
     *
     * <p>返回的字符串可直接传入 ChatClient：
     * <pre>
     * PromptPrompt prompt = PromptTemplateService.buildPrompt(question, chunks);
     * chatClient.prompt()
     *     .system(prompt.systemText())
     *     .user(prompt.userText())
     *     .stream()
     *     .content();
     * </pre>
     *
     * @param question 用户提问
     * @param chunks   检索到的上下文 Chunk 列表（已按相关性降序）
     * @return 组装好的 Prompt 对象
     */
    public PromptResult buildPrompt(String question, List<RetrievalChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new PromptResult(
                    buildSystemPrompt(null),
                    buildUserPrompt(question, List.of()),
                    false  // 无有效上下文
            );
        }


        String systemText = buildSystemPrompt(chunks);
        String userText = buildUserPrompt(question, chunks);
        boolean hasContext = !chunks.isEmpty();

        return new PromptResult(systemText, userText, hasContext);
    }

    /**
     * 仅构建 System Prompt 部分。
     *
     * @param chunks 有效上下文（可为 null）
     */
    private String buildSystemPrompt(List<RetrievalChunk> chunks) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是严谨的中文知识问答助手。\n");
        sb.append("你的职责是根据用户提供的上下文（Context）准确回答问题。\n\n");

        sb.append("【核心原则】\n");
        sb.append("1. 必须且只能基于 Context 中的信息作答。\n");
        sb.append("2. 如果 Context 中没有相关信息，必须如实告知用户\"未找到相关信息\"，");
        sb.append("而不是编造答案或凭先验知识猜测。\n");
        sb.append("3. 回答应当简洁、准确、有条理，适当分段。\n");
        sb.append("4. 若 Context 中的信息不足以完整回答，可以说明\"部分信息不足\"，但不得臆造。\n\n");
        sb.append("5. 请回答中不要包含【根据提供的上下文】，要让提问者无感知\n\n");

        // 注入上下文 Chunk（供 LLM 感知可用的信息来源）
        if (chunks != null && !chunks.isEmpty()) {
            sb.append("【可用上下文】共 ").append(chunks.size()).append(" 个文档片段：\n");
            for (int i = 0; i < chunks.size(); i++) {
                RetrievalChunk c = chunks.get(i);
                String label = c.getTitle() != null && !c.getTitle().isBlank()
                        ? c.getTitle()
                        : "来源 " + (i + 1);
                sb.append("[")
                   .append(i + 1)
                   .append("] ")
                   .append(label)
                   .append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建 User Prompt（包含上下文片段和问题）。
     *
     * @param question 用户提问
     * @param chunks  有效上下文
     */
    private String buildUserPrompt(String question, List<RetrievalChunk> chunks) {
        StringBuilder sb = new StringBuilder();

        sb.append("【上下文】\n");
        if (chunks.isEmpty()) {
            sb.append("（未找到相关上下文）\n");
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                RetrievalChunk c = chunks.get(i);
                String label = c.getTitle() != null && !c.getTitle().isBlank()
                        ? c.getTitle()
                        : "来源 " + (i + 1);

                sb.append("--- 第 ").append(i + 1).append(" 片段 [")
                   .append(label).append("] ---\n")
                   .append(c.getContent())
                   .append("\n\n");
            }
        }

        sb.append("【问题】\n");
        sb.append(question).append("\n");

        return sb.toString();
    }

    /**
     * Prompt 构建结果，包含系统文本、用户文本以及是否有有效上下文标志。
     *
     * @param systemText  系统提示词
     * @param userText    用户提示词（包含上下文 + 问题）
     * @param hasContext  是否有有效上下文
     */
    public record PromptResult(
            String systemText,
            String userText,
            boolean hasContext
    ) {}
}
