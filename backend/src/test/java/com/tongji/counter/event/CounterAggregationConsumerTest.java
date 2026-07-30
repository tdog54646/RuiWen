package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.cache.RedisKeyScanner;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CounterAggregationConsumerTest {

    @Test
    void redisFailureEscapesWithoutAcknowledgment() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        Acknowledgment ack = mock(Acknowledgment.class);
        CounterAggregationConsumer consumer = new CounterAggregationConsumer(
                mapper, redis, mock(RedisKeyScanner.class));
        String message = mapper.writeValueAsString(
                CounterEvent.of("knowpost", "12", "like", 0, 7L, 1));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message, ack));
        verify(ack, never()).acknowledge();
    }
}
