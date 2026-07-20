package com.tongji.llm.rag.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.tongji.llm.rag.RetrievalContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridSearchServiceTest {

    @Test
    void vectorRequestUsesOnlyFilteredKnnWithoutUnfilteredTopLevelQuery() {
        Query privateFilter = Query.of(q -> q.term(t -> t
                .field("metadata.creatorId")
                .value(FieldValue.of("1"))));

        SearchRequest request = HybridSearchService.buildVectorSearchRequest(
                new float[]{0.1f, 0.2f}, 10, privateFilter);

        assertNull(request.query(), "顶层 query 必须为空，避免与 KNN 做 OR 后绕过私有库过滤");
        assertFalse(request.knn().isEmpty(), "请求必须包含 KNN 检索");
        assertTrue(request.toString().contains("metadata.creatorId"),
                "KNN 请求必须保留用户隔离 filter");
    }

    @Test
    void privateScopeRejectsPublicAndOtherUsersDocuments() {
        RetrievalContext privateContext = RetrievalContext.of(1L, RetrievalContext.Scope.PRIVATE);

        assertTrue(HybridSearchService.isSourceAuthorized(
                source("1", "private"), privateContext));
        assertFalse(HybridSearchService.isSourceAuthorized(
                source("1", "public"), privateContext));
        assertFalse(HybridSearchService.isSourceAuthorized(
                source("2", "private"), privateContext));
        assertFalse(HybridSearchService.isSourceAuthorized(
                source("2", "public"), privateContext));
    }

    @Test
    void allScopeKeepsPublicMineAndLegacyVisibilitySemantics() {
        RetrievalContext allContext = RetrievalContext.of(1L, RetrievalContext.Scope.ALL);

        assertTrue(HybridSearchService.isSourceAuthorized(source("2", "public"), allContext));
        assertTrue(HybridSearchService.isSourceAuthorized(source("1", "private"), allContext));
        assertTrue(HybridSearchService.isSourceAuthorized(source("2", null), allContext));
        assertFalse(HybridSearchService.isSourceAuthorized(source("2", "private"), allContext));
    }

    private static Map<String, Object> source(String creatorId, String visible) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("creatorId", creatorId);
        if (visible != null) {
            metadata.put("visible", visible);
        }
        return Map.of("metadata", metadata, "content", "test");
    }
}
