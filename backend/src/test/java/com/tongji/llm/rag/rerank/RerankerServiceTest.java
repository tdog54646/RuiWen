package com.tongji.llm.rag.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.llm.rag.model.RagProperties;
import com.tongji.llm.rag.model.RetrievalChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RerankerServiceTest {

    @Test
    void buildsDashScopeRequestAndParsesSortedScores() throws Exception {
        RagProperties properties = new RagProperties();
        properties.getRerank().setApiKey("test-key");
        properties.getRerank().setMinScore(0.2);
        ObjectMapper mapper = new ObjectMapper();
        RerankerService service = new RerankerService(properties, mapper);
        List<RetrievalChunk> chunks = List.of(
                RetrievalChunk.of("c0", "p0", "正文0", "标题0", 0),
                RetrievalChunk.of("c1", "p1", "正文1", "标题1", 0),
                RetrievalChunk.of("c2", "p2", "正文2", "标题2", 0));

        JsonNode request = mapper.readTree(service.buildRequest("问题", chunks, properties.getRerank()));
        assertEquals("问题", request.path("input").path("query").asText());
        assertEquals(3, request.path("input").path("documents").size());
        assertTrue(request.path("input").path("documents").get(0).asText().contains("标题0"));

        List<RetrievalChunk> result = service.parseResponse("""
                {"output":{"results":[
                  {"index":0,"relevance_score":0.35},
                  {"index":1,"relevance_score":0.91},
                  {"index":2,"relevance_score":0.10}
                ]}}
                """, chunks, properties.getRerank());
        assertEquals(List.of("p1", "p0"), result.stream().map(RetrievalChunk::getPostId).toList());
        assertEquals(0.91, result.getFirst().getRerankScore(), 1e-9);
    }

    @Test
    void availabilityDependsOnCredentialsNotProductionToggle() {
        RagProperties properties = new RagProperties();
        properties.getRerank().setEnabled(false);
        RerankerService service = new RerankerService(properties, new ObjectMapper());
        assertFalse(service.isAvailable());
        properties.getRerank().setApiKey("test-key");
        assertTrue(service.isAvailable());
    }
}
