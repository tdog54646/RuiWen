package com.tongji.qa.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.qa.service.QaMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MemoryUpdateConsumerTest {

    @Test
    void failedRegenerationIsNotAcknowledged() {
        QaMemoryService memoryService = mock(QaMemoryService.class);
        doThrow(new RuntimeException("llm unavailable")).when(memoryService).regenerateMemories(7L);
        Acknowledgment ack = mock(Acknowledgment.class);
        MemoryUpdateConsumer consumer = new MemoryUpdateConsumer(new ObjectMapper(), memoryService);

        assertThrows(IllegalStateException.class,
                () -> consumer.onMemoryUpdate("{\"userId\":7}", ack));
        verify(ack, never()).acknowledge();
    }
}
