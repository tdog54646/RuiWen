package com.tongji.llm.api;


import com.tongji.llm.rag.model.RagProperties;
import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.llm.rag.query.HybridRagQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 检索与问答 REST 接口。
 * <p>
 * 提供两种调用方式：
 * <ol>
 *   <li><b>流式问答（{@code POST /api/rag/query}）。</b>
 *       执行混合检索 + LLM 生成，通过 SSE（Server-Sent Events）流式返回。
 *       适用于前端实时展示打字效果。</li>
 *   <li><b>仅检索（{@code GET /api/rag/chunks}）。</b>
 *       仅返回 Top N 相关 Chunk，不调用 LLM。适用于调试或需要自行组装 Prompt 的场景。</li>
 * </ol>
 *
 * @see HybridRagQueryService
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/rag", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RagController {

    private final HybridRagQueryService hybridRagQueryService;
    private final RagProperties ragProperties;

    // -------------------------------------------------------------------------
    // 流式问答
    // -------------------------------------------------------------------------

    /**
     * 流式 RAG 问答接口。
     * <p>
     * 执行流程：语义缓存检查 → 混合检索（向量+BM25+RRF） → Prompt 组装 → LLM 流式生成。
     *
     * <p><b>请求示例（cURL）：</b>
     * <pre>
     * curl -X POST http://localhost:8080/api/rag/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question":"什么是 RAG？","topK":5,"maxTokens":500}'
     * </pre>
     *
     * <p><b>流式响应（SSE）：</b>
     * <pre>
     * data:{"content":"RAG 是","done":false}
     * data:{"content":"一种","done":false}
     * data:{"content":"检索增强","done":false}
     * data:{"content":"生成技术。","done":true}
     * </pre>
     *
     * <p>若将 {@code Accept} 头设为 {@code text/event-stream}，则返回纯 SSE 流
     * （每个 event data 为纯文本片段，无 JSON 包装）。
     *
     * @param request 请求体：包含 question（必填）、topK（默认 5）、maxTokens（默认 500）
     * @return SSE 流，每个事件 data 为 JSON：{"content":"...", "done":false/true}
     */
    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ServerSentEvent<RagAnswerChunk>> query(@RequestBody RagQueryRequest request) {
        String question = request.question();
        //取默认值
        int topK = request.topK() != null && request.topK() > 0 ? request.topK() : 5;
        int maxTokens = request.maxTokens() != null && request.maxTokens() > 0 ? request.maxTokens() : 800;

        if (question == null || question.isBlank()) {
            return Flux.just(new RagAnswerChunk("", true).toSSE());
        }

        log.info("RAG query: question={}, topK={}, maxTokens={}", truncate(question, 40), topK, maxTokens);

        return hybridRagQueryService.streamAnswer(question, topK, maxTokens)
                .map(content -> new RagAnswerChunk(content, false).toSSE())
                .concatWith(Flux.defer(() -> Flux.just(new RagAnswerChunk("", true).toSSE())));
    }

    /**
     * 流式 RAG 问答接口（纯 SSE，不含 JSON 包装）。
     * <p>
     * 当请求的 {@code Accept} 头为 {@code text/event-stream} 时返回此端点。
     * 每个 SSE 事件的 data 字段直接为 LLM 输出片段（不含 JSON）。
     *
     * <p><b>请求示例（cURL）：</b>
     * <pre>
     * curl -N -X POST http://localhost:8080/api/rag/query/stream \
     *   -H "Content-Type: application/json" \
     *   -H "Accept: text/event-stream" \
     *   -d '{"question":"RAG 是什么？","topK":5,"maxTokens":500}'
     * </pre>
     *
     * <p><b>响应格式（纯文本 SSE）：</b>
     * <pre>
     * data:RAG 是
     * data:一种
     * data:检索增强
     * data:生成技术。
     * </pre>
     *
     * @param request 请求体
     * @return 纯文本 SSE 流
     */
    @PostMapping(path = "/query/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> queryStream(@RequestBody RagQueryRequest request) {
        String question = request.question();
        //取默认值
        int topK = request.topK() != null && request.topK() > 0 ? request.topK() : 5;
        int maxTokens = request.maxTokens() != null && request.maxTokens() > 0 ? request.maxTokens() : 800;

        if (question == null || question.isBlank()) {
            return Flux.just(ServerSentEvent.builder("")
                    .comment("[DONE]")
                    .build());
        }

        return hybridRagQueryService.streamAnswer(question, topK, maxTokens)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .doOnComplete(() -> log.info("SSE stream completed for question: {}", truncate(question, 30)));
    }

    // -------------------------------------------------------------------------
    // 仅检索（不生成）
    // -------------------------------------------------------------------------

    /**
     * 仅检索接口：返回 Top N 相关 Chunk，不调用 LLM。
     * <p>
     * 用于：
     * <ul>
     *   <li>调试检索质量（查看召回的 Chunk 是否相关）</li>
     *   <li>前端自行展示"相关文档片段"列表</li>
     *   <li>需要自行组装 Prompt 或接入其他 LLM 时的上游数据</li>
     * </ul>
     *
     * <p><b>请求示例（cURL）：</b>
     * <pre>
     * curl "http://localhost:8080/api/rag/chunks?question=RAG%20是什么&topK=5"
     * </pre>
     *
     * <p><b>响应示例：</b>
     * <pre>
     * [
     *   {
     *     "chunkId": "12345#0",
     *     "postId": "12345",
     *     "content": "RAG（Retrieval-Augmented Generation）是一种...",
     *     "title": "RAG 技术详解",
     *     "position": 0,
     *     "rrfScore": 0.028
     *   }
     * ]
     * </pre>
     *
     * @param question 用户查询（必填）
     * @param topK     返回数量上限（默认 5）
     * @return 检索到的 Chunk 列表，按 RRF 综合得分降序排列
     */
    @GetMapping(path = "/chunks", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RagChunkResponse> getChunks(
            @RequestParam("question") String question,
            @RequestParam(value = "topK", defaultValue = "5") int topK
    ) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        log.debug("RAG chunks query: question={}, topK={}", truncate(question, 40), topK);

        List<RetrievalChunk> chunks = hybridRagQueryService.retrieveOnly(question, topK);
        return chunks.stream()
                .map(c -> new RagChunkResponse(
                        c.getChunkId(),
                        c.getPostId(),
                        c.getContent(),
                        c.getTitle(),
                        c.getPosition(),
                        c.getRrfScore()
                ))
                .toList();
    }

    // -------------------------------------------------------------------------
    // DTO
    // -------------------------------------------------------------------------

    /**
     * 流式问答请求体。
     *
     * @param question  用户提问（必填）
     * @param topK     检索 Chunk 数量上限（默认取配置中的 context-limit）
     * @param maxTokens LLM 最大生成 token 数（默认 800）
     */
    public record RagQueryRequest(
            String question,
            @org.springframework.lang.Nullable Integer topK,
            @org.springframework.lang.Nullable Integer maxTokens
    ) {}
    /**
     * 流式问答的 SSE 事件数据。
     *
     * @param content LLM 当前输出的文本片段
     * @param done    是否为最后一个事件（流结束标志）
     */
    public record RagAnswerChunk(String content, boolean done) {
        public ServerSentEvent<RagAnswerChunk> toSSE() {
            return ServerSentEvent.<RagAnswerChunk>builder()
                    .data(this)
                    .build();
        }
    }

    /**
     * Chunk 检索结果响应。
     *
     * @param chunkId  Chunk 全局唯一 ID
     * @param postId   所属帖子 ID
     * @param content  Chunk 文本内容
     * @param title    所属帖子标题
     * @param position 在文章中的位置序号
     * @param rrfScore RRF 综合得分
     */
    public record RagChunkResponse(
            String chunkId,
            String postId,
            String content,
            String title,
            int position,
            double rrfScore
    ) {}

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
