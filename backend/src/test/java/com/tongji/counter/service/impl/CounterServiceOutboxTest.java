package com.tongji.counter.service.impl;

import com.tongji.cache.RedisKeyScanner;
import com.tongji.counter.event.CounterEventProducer;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CounterServiceOutboxTest {

    @Test
    void kafkaFailureKeepsSuccessfulBitmapChangeDurablyQueued() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        CounterEventProducer producer = mock(CounterEventProducer.class);
        when(producer.serialize(any())).thenReturn("{\"eventId\":\"evt-1\"}");
        doThrow(new IllegalStateException("broker unavailable")).when(producer).publish(any());
        ApplicationEventPublisher localEvents = mock(ApplicationEventPublisher.class);
        CounterServiceImpl service = new CounterServiceImpl(
                redis, producer, localEvents, mock(RedissonClient.class), mock(RedisKeyScanner.class));

        assertTrue(service.like("knowpost", "12", 7L));
        verify(localEvents).publishEvent((Object) any(com.tongji.counter.event.CounterEvent.class));
        verify(hashes, never()).delete(eq(CounterEventProducer.OUTBOX_KEY), any());
    }
}
