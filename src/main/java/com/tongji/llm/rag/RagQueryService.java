package com.tongji.llm.rag;

import com.tongji.llm.cache.SemanticCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RAG 问答查询服务：
 * - 在问答前保障索引，检索相关上下文并构造提示词
 * - 通过 ChatClient 以流式（SSE）方式返回模型输出
 */
@Service
@RequiredArgsConstructor
public class RagQueryService {
    // 向量检索接口（Elasticsearch 向量库封装）
    private final VectorStore vectorStore;
    // 大模型对话客户端（在 LlmConfig 中通过 @Qualifier 绑定 deepSeekChatModel）
    private final ChatClient chatClient;
    // 索引服务：确保帖子在问答前已建立/更新索引
    private final RagIndexService indexService;
    // 语义缓存 + embedding
    private final SemanticCacheService semanticCache;
    private final EmbeddingService embeddingService;

    /**
     * 使用 WebFlux 返回回答内容的流。
     */
    public Flux<String> streamAnswerFlux(long postId, String question, int topK, int maxTokens) {
        // 1) 语义缓存：先查 Redis
        String namespace = "post:" + postId;
        float[] qVec = SemanticCacheService.l2Normalize(embeddingService.embedQuery(question));
        double threshold = 0.95;
        int scanLimit = 200;
        long ttlSeconds = 7 * 24 * 3600;

        var hitOpt = semanticCache.getIfHit(namespace, qVec, threshold, scanLimit);
        if (hitOpt.isPresent()) {
            return Flux.just(hitOpt.get().answer());
        }

        // 2) 未命中：走原 RAG
        indexService.ensureIndexed(postId);

        List<String> contexts = searchContexts(String.valueOf(postId), question, Math.max(1, topK));
        String context = String.join("\n\n---\n\n", contexts);

        String system = "你是中文知识助手。只能依据提供的知文上下文回答；无法确定的请说明不确定。";
        String user = "问题：" + question + "\n\n上下文如下（可能不完整）：\n" + context + "\n\n请基于以上上下文作答。";

        // 用于在单次订阅中“顺手”收集大模型返回的所有片段
        List<String> answerChunks = new ArrayList<>();

        return chatClient
                .prompt()
                .system(system)
                .user(user)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .temperature(0.2)
                        .maxTokens(maxTokens)
                        .build())
                .stream()
                .content()
                // 关键修改 1：利用 doOnNext 边输出边收集，绝不触发二次订阅
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        answerChunks.add(chunk);
                    }
                })
                // 关键修改 2：流彻底输出完毕后，异步写回缓存
                .doOnComplete(() -> {
                    String answer = String.join("", answerChunks);
                    String cacheId = UUID.randomUUID().toString();

                    // 将可能阻塞的 Redis/DB 写入操作丢到弹性线程池，避免卡死 WebFlux 主线程
                    Mono.fromRunnable(() -> {
                                semanticCache.put(namespace, cacheId, question, qVec, answer, ttlSeconds);
                            })
                            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                            .subscribe(); // Fire and forget (只管发射，不管结果)
                });
    }
    /**
     * 语义检索上下文：
     * - 先进行宽召回（fetchK ≥ 3×topK，至少 20）提高召回率
     * - 再按 metadata.postId 做服务端过滤，避免跨帖子污染
     */
    private List<String> searchContexts(String postId, String query, int topK) {
        int fetchK = Math.max(topK * 3, 20); // 宽召回：扩大初始检索集合
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(fetchK).build() // 语义相似检索
        );
        List<String> out = new ArrayList<>(topK);
        for (Document d : docs) {
            Object pid = d.getMetadata().get("postId");
            if (pid != null && postId.equals(String.valueOf(pid))) { // 仅保留当前帖子对应的切片
                String txt = d.getText();
                if (txt != null && !txt.isEmpty()) {
                    out.add(txt);
                    if (out.size() >= topK) break; // 只取前 topK 个上下文
                }
            }
        }
        return out;
    }
}