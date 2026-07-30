package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CounterEventProducerTest {

    @Test
    void brokerFailureIsPropagatedToTheWritePath() {
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));
        CounterEventProducer producer = new CounterEventProducer(
                kafka, new ObjectMapper(), mock(StringRedisTemplate.class), 1L);

        assertThrows(IllegalStateException.class,
                () -> producer.publish(CounterEvent.of("knowpost", "12", "like", 0, 7L, 1)));
    }
}
