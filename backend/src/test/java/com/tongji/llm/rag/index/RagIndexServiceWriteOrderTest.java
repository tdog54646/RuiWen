package com.tongji.llm.rag.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.llm.rag.chunk.SemanticChunker;
import com.tongji.storage.OssStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagIndexServiceWriteOrderTest {

    @Test
    void failedAddDoesNotDeleteExistingChunksFirst() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        OssStorageService storage = mock(OssStorageService.class);
        ElasticsearchClient es = mock(ElasticsearchClient.class);
        EsProperties properties = new EsProperties();
        properties.setIndex("test-rag-index");
        SemanticChunker chunker = mock(SemanticChunker.class);
        RestTemplate http = mock(RestTemplate.class);
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setId(12L);
        row.setCreatorId(7L);
        row.setStatus("published");
        row.setVisible("public");
        row.setContentUrl("https://content.example/12.md");
        row.setContentSha256("a".repeat(64));
        row.setTitle("标题");
        when(mapper.findDetailById(12L)).thenReturn(row);
        @SuppressWarnings("unchecked") SearchResponse<java.util.Map> searchResponse = mock(SearchResponse.class);
        @SuppressWarnings("unchecked") HitsMetadata<java.util.Map> hits = mock(HitsMetadata.class);
        when(searchResponse.hits()).thenReturn(hits);
        when(hits.hits()).thenReturn(List.of());
        when(es.search(any(java.util.function.Function.class), eq(java.util.Map.class)))
                .thenReturn(searchResponse);
        when(http.exchange(eq(row.getContentUrl()), any(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("正文".getBytes(StandardCharsets.UTF_8)));
        when(chunker.chunk("正文")).thenReturn(List.of("正文"));
        doThrow(new IllegalStateException("embedding unavailable")).when(vectorStore).add(anyList());
        RagIndexService service = new RagIndexService(
                vectorStore, mapper, storage, es, properties, chunker);
        ReflectionTestUtils.setField(service, "http", http);

        assertThrows(IllegalStateException.class, () -> service.rebuildSinglePost(12L));
        verify(vectorStore, never()).delete(anyList());
        verify(es).search(any(java.util.function.Function.class), eq(java.util.Map.class));
        verifyNoMoreInteractions(es);
    }
}
