package com.tongji.knowpost.api;

import com.tongji.knowpost.api.dto.HotQuestionResponse;
import com.tongji.llm.rag.RagIndexService;
import com.tongji.llm.rag.RagQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/knowposts")
@Validated
@RequiredArgsConstructor
public class KnowPostRagController {

    private final RagIndexService indexService;
    private final RagQueryService ragQueryService;

    /**
     * 单篇知文 RAG 问答（WebFlux + Flux 流式输出）。
     * 示例：GET /api/knowposts/{id}/qa/stream?question=...&topK=5&maxTokens=1024
     */
    @GetMapping(value = "/{id}/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> qaStream(@PathVariable("id") long id,
                                 @RequestParam("question") String question,
                                 @RequestParam(value = "topK", defaultValue = "5") int topK,
                                 @RequestParam(value = "maxTokens", defaultValue = "1024") int maxTokens) {
        return ragQueryService.streamAnswerFlux(id, question, topK, maxTokens);
    }

    /**
     * 单篇知文热点问答：从热点候选集随机返回 1 条问题文案。
     */
    @GetMapping("/{id}/qa/hotquestion")
    public HotQuestionResponse hotQuestion(@PathVariable("id") long id,
                                           @RequestParam(value = "limit", defaultValue = "10") @Min(1) @Max(50) int limit) {
        RagQueryService.HotQuestionResult result = ragQueryService.getHotQuestion(id, limit);
        return new HotQuestionResponse(result.postId(), result.question());
    }

    /**
     * 手动触发单篇索引重建（返回重建的切片数）。
     */
    @PostMapping("/{id}/rag/reindex")
    public int reindex(@PathVariable("id") long id) {
        return indexService.reindexSinglePost(id);
    }
}