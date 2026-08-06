package com.tongji.llm.rag.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.tongji.llm.rag.RetrievalContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void bm25QueryAppliesFieldWeightsPhraseSlopAndMinimumMatch() {
        Query filter = Query.of(q -> q.matchAll(m -> m));

        Query query = HybridSearchService.buildBm25Query(
                "Spring Boot 自动配置",
                filter,
                new Bm25QueryOptions(2.0, 1.5, 2, "50%", "best_fields", "NONE"));

        var should = query.bool().should();
        assertEquals(3, should.size());
        assertTrue(should.get(0).multiMatch().fields().contains("metadata.title^2.0"));
        assertEquals("50%", should.get(0).multiMatch().minimumShouldMatch());
        assertEquals(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields,
                should.get(0).multiMatch().type());
        assertNull(should.get(0).multiMatch().fuzziness());
        assertEquals(2, should.get(1).matchPhrase().slop());
        assertEquals(1.5f, should.get(1).matchPhrase().boost());
        assertEquals(3.0f, should.get(2).matchPhrase().boost());
    }

    @Test
    void bm25QueryOmitsPhraseBranchesWhenPhraseBoostIsZero() {
        Query query = HybridSearchService.buildBm25Query(
                "缓存穿透",
                Query.of(q -> q.matchAll(m -> m)),
                new Bm25QueryOptions(1.0, 0.0, 0, "1", "most_fields", "AUTO"));

        assertEquals(1, query.bool().should().size());
        assertEquals(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.MostFields,
                query.bool().should().getFirst().multiMatch().type());
        assertEquals("AUTO", query.bool().should().getFirst().multiMatch().fuzziness());
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
    void allScopeAllowsPublicAndMineButRejectsLegacyDocumentsFromOthers() {
        RetrievalContext allContext = RetrievalContext.of(1L, RetrievalContext.Scope.ALL);

        assertTrue(HybridSearchService.isSourceAuthorized(source("2", "public"), allContext));
        assertTrue(HybridSearchService.isSourceAuthorized(source("1", "private"), allContext));
        assertTrue(HybridSearchService.isSourceAuthorized(source("1", null), allContext));
        assertFalse(HybridSearchService.isSourceAuthorized(source("2", null), allContext));
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
