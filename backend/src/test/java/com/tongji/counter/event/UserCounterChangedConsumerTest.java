package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserCounterChangedConsumerTest {

    @Test
    void redisFailureEscapesWithoutAcknowledgment() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        when(mapper.findById(12L)).thenReturn(KnowPost.builder()
                .id(12L).creatorId(7L).status("published").build());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));
        Acknowledgment ack = mock(Acknowledgment.class);
        UserCounterChangedConsumer consumer = new UserCounterChangedConsumer(
                objectMapper, mapper, redis, mock(UserCounterService.class));
        String message = objectMapper.writeValueAsString(
                CounterEvent.of("knowpost", "12", "like", 0, 8L, 1));

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message, ack));
        verify(ack, never()).acknowledge();
    }
}
