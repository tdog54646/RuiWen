package com.tongji.relation.processor;

import com.tongji.counter.service.UserCounterService;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelationEventProcessorTest {

    @Test
    void failedSideEffectDoesNotWriteDoneMarker() {
        RelationMapper mapper = mock(RelationMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenReturn(false);
        when(values.setIfAbsent(startsWith("dedup:rel:lock:"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(mapper.existsFollowing(7L, 8L)).thenReturn(1);
        doThrow(new IllegalStateException("db unavailable"))
                .when(mapper).insertFollower(99L, 8L, 7L, 1);
        RelationEventProcessor processor = new RelationEventProcessor(
                mapper, redis, mock(UserCounterService.class));
        RelationEvent event = new RelationEvent(
                "FollowCreated", 7L, 8L, 99L, "evt-1", 1234L);

        assertThrows(IllegalStateException.class, () -> processor.process(event));
        verify(values, never()).set(startsWith("dedup:rel:done:"), eq("1"), any(Duration.class));
    }
}
