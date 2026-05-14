package com.tongji.llm.rag.query;

import com.tongji.llm.cache.SemanticCacheService;
import com.tongji.llm.rag.embedding.EmbeddingService;
import com.tongji.llm.rag.index.RagIndexService;
import com.tongji.llm.rag.model.RagProperties;
import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.llm.rag.search.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 混合 RAG 查询服务（Hybrid RAG Query Orchestrator）。
 * <p>
 * 这是整个 RAG 链路的编排层，负责串联阶段 2（检索）和阶段 3（生成）：
 * <ol>
 *   <li><b>语义缓存检查。</b> 先查 Redis 语义缓存，命中则直接返回，避免重复调用 LLM。</li>
 *   <li><b>混合检索。</b> 调用 {@link HybridSearchService} 执行向量 + BM25 + RRF 融合，
 *       返回 Top N 相关 Chunk。</li>
 *   <li><b>Prompt 组装。</b> 调用 {@link PromptTemplateService} 将 Chunk 拼入 System/User Prompt。</li>
 *   <li><b>生成。</b> 调用 {@link ChatClient} 生成回答，流式返回 SSE。</li>
 *   <li><b>缓存回写。</b> 流结束后异步将结果写入语义缓存。</li>
 * </ol>
 *
 * <h2>性能考量</h2>
 * <ul>
 *   <li>语义缓存在检索前执行，可节省一次 LLM 调用（延迟 ~200-500ms）。</li>
 *   <li>缓存回写使用 {@code boundedElastic} 线程池异步执行，不阻塞主响应流。</li>
 *   <li>若检索结果为空，直接返回兜底文案，跳过 LLM 调用以节省成本。</li>
 * </ul>
 *
 * @see HybridSearchService
 * @see PromptTemplateService
 * @see SemanticCacheService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRagQueryService {

    private final HybridSearchService hybridSearchService;
    private final PromptTemplateService promptTemplateService;
    private final SemanticCacheService semanticCache;
    private final EmbeddingService embeddingService;
    private final ChatClient chatClient;
    private final RagProperties properties;

    /**
     * 流式问答：执行混合 RAG 检索并流式返回 LLM 生成内容。
     * <p>
     * 完整链路：
     * <ol>
     *   <li>检查语义缓存 → 命中则直接返回缓存答案</li>
     *   <li>执行混合检索（向量 + BM25 + RRF）</li>
     *   <li>组装 Prompt → 检查是否有有效上下文</li>
     *   <li>有上下文：调用 LLM 流式生成，同时边输出边收集答案片段</li>
     *   <li>流结束后（doOnComplete）：异步回写语义缓存</li>
     * </ol>
     *
     * <p><b>注意：</b>同一问题第二次调用时，若语义缓存命中，会跳过 LLM 调用。
     * 语义缓存的 TTL 可通过 {@code semantic-cache.*} 配置项调整。
     *
     * @param question   用户提问
     * @param topK       检索并送入 LLM 的 Chunk 数量上限
     * @param maxTokens  LLM 最大生成 token 数
     * @return LLM 输出内容的流（SSE text/event-stream 友好）
     */
    public Flux<String> streamAnswer(String question, int topK, int maxTokens) {
        if (question == null || question.isBlank()) {
            return Flux.just(properties.getPrompt().getEmptyAnswer());
        }

        // Step 1: 语义缓存检查
        float[] qVec = embeddingService.embedQuery(question);
        if (qVec != null && qVec.length > 0) {
            float[] normalized = SemanticCacheService.l2Normalize(qVec);
            var hit = semanticCache.getIfHit(
                    "rag:global",        // 全局 RAG 缓存 namespace（可按场景细分）
                    normalized,
                    0.98,               // 余弦相似度阈值：>0.98 才视为命中
                    200                 // 扫描候选数量上限
            );
            if (hit.isPresent()) {
                log.info("Semantic cache hit for query: {}", truncate(question, 30));
                return Flux.just(hit.get().answer());
            }
        }

        // Step 2: 混合检索
        List<RetrievalChunk> chunks = hybridSearchService.hybridSearch(question, topK);
        log.debug("Hybrid search returned {} chunks for query: {}", chunks.size(), truncate(question, 30));

        // Step 3: 组装 Prompt
        PromptTemplateService.PromptResult promptResult = promptTemplateService.buildPrompt(question, chunks);

        // Step 4: 无有效上下文 → 直接返回兜底文案，跳过 LLM 调用
        if (!promptResult.hasContext()) {
            log.info("No relevant context found for query: {}", truncate(question, 30));
            return Flux.just(properties.getPrompt().getEmptyAnswer());
        }

        // Step 5: 流式调用 LLM，同时边输出边收集答案片段
        List<String> answerFragments = new ArrayList<>();

        Flux<String> llmFlux = chatClient
                .prompt()
                .system(promptResult.systemText())
                .user(promptResult.userText())
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.2)   // 低温：保证答案确定性，减少幻觉
                        .maxTokens(maxTokens)
                        .build())
                .stream()
                .content();

        // Step 6: 边输出边收集答案，流结束后异步回写缓存
        return llmFlux
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        answerFragments.add(chunk);
                    }
                })
                .doOnComplete(() -> {
                    String fullAnswer = String.join("", answerFragments);
                    if (!fullAnswer.isBlank() && qVec != null && qVec.length > 0) {
                        String cacheId = UUID.randomUUID().toString();
                        // 写入弹性线程池，不阻塞主响应线程
                        Mono.fromRunnable(() -> {
                                    float[] cacheVec = SemanticCacheService.l2Normalize(qVec);
                                    semanticCache.put(
                                            "rag:global",
                                            cacheId,
                                            question,
                                            cacheVec,
                                            fullAnswer,
                                            7 * 24 * 3600L  // 默认 7 天 TTL
                                    );
                                })
                                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                                .subscribe();
                        log.debug("Answer cached for query: {}", truncate(question, 30));
                    }
                });
    }

    /**
     * 非流式问答：先完整检索和生成，再一次性返回结果。
     * <p>
     * 适用于不需要流式输出的场景（如后台处理、API 调用）。
     *
     * @param question  用户提问
     * @param topK     检索 Chunk 数量上限
     * @param maxTokens LLM 最大生成 token 数
     * @return 完整回答字符串
     */
    public String generateAnswer(String question, int topK, int maxTokens) {
        List<String> fragments = new ArrayList<>();
        streamAnswer(question, topK, maxTokens)
                .doOnNext(fragments::add)
                .blockLast();   // 等待流结束
        return String.join("", fragments);
    }

    /**
     * 仅检索（不生成）：返回检索到的 Chunk 列表。
     * <p>
     * 用于调试或需要自行处理上下文的场景。
     *
     * @param question 用户提问
     * @param topK    返回的 Chunk 数量上限
     * @return 检索结果列表（已按 RRF 综合得分降序排列）
     */
    public List<RetrievalChunk> retrieveOnly(String question, int topK) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        return hybridSearchService.hybridSearch(question, topK);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
