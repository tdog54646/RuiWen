package com.tongji.knowpost.api;

import com.tongji.llm.rag.index.RagIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 知文 RAG 索引管理接口。
 * <p>问答检索已统一走 {@code /api/qa/chat}（{@link com.tongji.qa.service.QaChatService}），
 * 本类仅保留手动索引重建入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowposts")
@RequiredArgsConstructor
public class KnowPostRagController {

    private final RagIndexService indexService;

    /**
     * 手动触发单篇索引重建（返回重建的切片数）。
     */
    @PostMapping("/{id}/rag/reindex")
    public int reindex(@PathVariable("id") long id) {
        return indexService.reindexSinglePost(id);
    }
}
