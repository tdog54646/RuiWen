package com.tongji.llm.rag.search;

import com.tongji.llm.rag.model.RetrievalChunk;

import java.util.List;

/** 最终召回结果及其完整检索轨迹。 */
public record RetrievalSearchResult(List<RetrievalChunk> chunks, RetrievalTrace trace) {
    public RetrievalSearchResult {
        chunks = List.copyOf(chunks);
    }
}
