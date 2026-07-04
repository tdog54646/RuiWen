package com.tongji.relation.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public void insert(String aggregateType, Long aggregateId, String eventType, Object data) {
        Long id = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        OutboxPayload payload = new OutboxPayload(aggregateType, aggregateId, eventType, data);
        try {
            mapper.insert(id, aggregateType, aggregateId, eventType, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }

    public record OutboxPayload(
            String aggregateType,
            Long aggregateId,
            String eventType,
            Object data
    ) {
    }
}
