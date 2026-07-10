package com.tongji.qa.service;

import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.qa.config.QaProperties;
import com.tongji.qa.model.QaMessage;
import com.tongji.qa.model.UserMemory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话 Prompt 组装器。
 * <p>负责将【用户记忆 + RAG 上下文 + 历史窗口 + 当前问题】组装为 Spring AI 的 {@link Message} 列表。
 * System Prompt 与记忆总结 Prompt 模板从 classpath:qa-prompts/ 加载，便于调整。
 */
@Service
@RequiredArgsConstructor
public class QaPromptAssembler {

    private static final String SYSTEM_TEMPLATE_PATH = "qa-prompts/multi-turn-system.md";
    private static final String MEMORY_SUMMARY_TEMPLATE_PATH = "qa-prompts/memory-summary.md";

    private final QaProperties properties;

    private String systemTemplate;
    private String memorySummaryTemplate;

    @PostConstruct
    void init() throws IOException {
        this.systemTemplate = readClasspath(SYSTEM_TEMPLATE_PATH);
        this.memorySummaryTemplate = readClasspath(MEMORY_SUMMARY_TEMPLATE_PATH);
    }

    /**
     * 组装多轮对话的 Message 列表：System(角色+记忆) + 历史窗口 + 当轮 User(问题+RAG上下文)。
     *
     * @param question 当前问题
     * @param chunks   RAG 检索到的上下文（可为空）
     * @param history  历史窗口消息（正序，最近 N 轮，不含当轮）
     * @param memories 用户已启用记忆条目（可为空）
     * @return 可直接传入 {@code ChatClient.prompt().messages(...)} 的消息列表
     */
    public List<Message> assemble(String question, List<RetrievalChunk> chunks,
                                  List<QaMessage> history, List<UserMemory> memories) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(buildSystemText(memories)));

        if (history != null) {
            for (QaMessage h : history) {
                if ("assistant".equalsIgnoreCase(h.getRole())) {
                    messages.add(new AssistantMessage(safe(h.getContent())));
                } else {
                    messages.add(new UserMessage(safe(h.getContent())));
                }
            }
        }

        messages.add(new UserMessage(buildCurrentUserText(question, chunks)));
        return messages;
    }

    /**
     * 构建记忆总结 prompt（替换 {dialog} 占位符）。
     */
    public String buildMemorySummaryPrompt(String dialog) {
        return memorySummaryTemplate.replace("{dialog}", dialog == null ? "" : dialog);
    }

    private String buildSystemText(List<UserMemory> memories) {
        String memText;
        if (memories == null || memories.isEmpty()) {
            memText = "（暂无）";
        } else {
            StringBuilder sb = new StringBuilder();
            for (UserMemory m : memories) {
                sb.append("- [").append(safe(m.getCategory())).append("] ")
                  .append(safe(m.getContent())).append("\n");
            }
            memText = sb.toString().trim();
        }
        return systemTemplate.replace("{memories}", memText);
    }

    private String buildCurrentUserText(String question, List<RetrievalChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        if (chunks != null && !chunks.isEmpty()) {
            sb.append("【知识库上下文】\n");
            for (int i = 0; i < chunks.size(); i++) {
                RetrievalChunk c = chunks.get(i);
                String label = (c.getTitle() != null && !c.getTitle().isBlank())
                        ? c.getTitle()
                        : ("来源 " + (i + 1));
                sb.append("--- [").append(label).append("] ---\n")
                  .append(safe(c.getContent())).append("\n\n");
            }
            sb.append("【问题】\n").append(safe(question));
        } else {
            sb.append(safe(question));
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String readClasspath(String path) throws IOException {
        try (var is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
