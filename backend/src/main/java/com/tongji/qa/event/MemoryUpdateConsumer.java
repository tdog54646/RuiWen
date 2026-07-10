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
 * 异步刷新用户画像。失败时仍 ack 以避免毒丸阻塞分区（下次达阈值会再次触发，最终一致）。
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
            // 失败不阻塞分区：下次累计达阈值会再次触发，最终一致
            log.error("Memory auto-update failed (message={}): {}", truncate(message, 200), e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
