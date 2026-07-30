package com.tongji.qa.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.qa.service.QaMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * 记忆自动更新消费者。
 * <p>消费 {@link QaTopics#MEMORY_UPDATE} 事件，调 {@link QaMemoryService#regenerateMemories(long)}
 * 异步刷新用户画像。失败抛给全局 Kafka 重试/DLT，避免静默丢失更新事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryUpdateConsumer {

    private final ObjectMapper objectMapper;
    private final QaMemoryService memoryService;

    @KafkaListener(topics = QaTopics.MEMORY_UPDATE, groupId = "qa-memory-llm")
    public void onMemoryUpdate(String message, Acknowledgment ack) {
        try {
            MemoryUpdateEvent evt = objectMapper.readValue(message, MemoryUpdateEvent.class);
            log.info("Auto-refreshing memories for user {}", evt.getUserId());
            memoryService.regenerateMemories(evt.getUserId());
        } catch (Exception e) {
            log.error("Memory auto-update failed; delegating to retry/DLT (message={})",
                    truncate(message, 200), e);
            throw new IllegalStateException("记忆自动更新失败", e);
        }
        ack.acknowledge();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
