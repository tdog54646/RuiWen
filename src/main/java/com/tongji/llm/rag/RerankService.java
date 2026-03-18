package com.tongji.llm.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 简单的重排序服务：将检索到的文档和查询发送到一个打分模型，
 * 要求模型返回与每个文档对应的相关性分数数组，范围 0.0 - 1.0。
 * 实际可替换为更合适的 cross-encoder 接口。
 */
@Service
@RequiredArgsConstructor
public class RerankService {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对 docs 按顺序打分，返回与 docs 等长的分数列表（0..1）。
     * 在生产中应使用专门的 cross-encoder 模型或本地打分器以避免高延迟和 token 开销。
     */
    public List<Double> score(List<Document> docs, String query) {
        if (docs == null || docs.isEmpty()) return Collections.emptyList();
        // 构造简短 prompt：要求严格返回 JSON 数组
        StringBuilder user = new StringBuilder();
        user.append("Query: ").append(query).append("\n\n");
        user.append("Documents:\n");
        for (int i = 0; i < docs.size(); i++) {
            String txt = docs.get(i).getText();
            user.append("<<DOC_").append(i).append(">>\n");
            user.append(txt == null ? "" : txt).append("\n");
            user.append("<<END_DOC_").append(i).append(">>\n");
        }
        user.append("\n请为每个文档返回一个与 Query 的相关性分数（0.0 - 1.0），按文档顺序输出 JSON 数组，例：[0.12, 0.9, 0.0]。仅输出 JSON 数组，不要额外的文字。");

        try {
            String resp = chatClient
                    .prompt()
                    .system("你是一个语义相关性打分器。仅输出 JSON 数组。")
                    .user(user.toString())
                    .options(DeepSeekChatOptions.builder()
                            .model("cross-encoder") // 生产中换成实际可用的打分模型
                            .temperature(0.0)
                            .maxTokens(512)
                            .build())
                    .call()
                    .content();

            // 解析 JSON 为 List<Double>
            return objectMapper.readValue(resp, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            // 打分失败时返回空分数列表，调用方可决定降级策略
            return Collections.emptyList();
        }
    }
}
