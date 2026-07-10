package com.tongji.qa.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 多轮问答事件生产者：将记忆更新等事件异步发送到 Kafka，不阻塞问答主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaEventProducer {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    /**
     * 发布记忆自动更新事件。
     */
    public void publishMemoryUpdate(long userId) {
        try {
            String payload = objectMapper.writeValueAsString(MemoryUpdateEvent.of(userId));
            kafka.send(QaTopics.MEMORY_UPDATE, payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize MemoryUpdateEvent for user {}: {}", userId, e.getMessage());
        }
    }
}
