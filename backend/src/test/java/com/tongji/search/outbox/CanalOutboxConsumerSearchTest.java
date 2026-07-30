package com.tongji.search.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.llm.rag.index.RagIndexService;
import com.tongji.search.index.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CanalOutboxConsumerSearchTest {

    @Test
    void indexFailureIsNotAcknowledgedAndEscapesForRetry() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SearchIndexService search = mock(SearchIndexService.class);
        RagIndexService rag = mock(RagIndexService.class);
        Acknowledgment ack = mock(Acknowledgment.class);
        doThrow(new IllegalStateException("es unavailable")).when(search).upsertKnowPost(12L);

        var payload = objectMapper.createObjectNode();
        payload.put("aggregateType", "knowpost");
        payload.put("aggregateId", 12L);
        payload.put("eventType", "KnowPostUpdated");
        payload.set("data", objectMapper.createObjectNode().put("op", "update"));
        var row = objectMapper.createObjectNode().put("payload", objectMapper.writeValueAsString(payload));
        var message = objectMapper.createObjectNode();
        message.put("table", "outbox");
        message.put("type", "INSERT");
        message.set("data", objectMapper.createArrayNode().add(row));
        CanalOutboxConsumerSearch consumer = new CanalOutboxConsumerSearch(objectMapper, search, rag);

        assertThrows(RuntimeException.class,
                () -> consumer.onMessage(objectMapper.writeValueAsString(message), ack));
        verify(ack, never()).acknowledge();
        verify(rag, never()).rebuildSinglePost(anyLong());
    }
}
