package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 计数事件生产者。
 *
 * <p>职责：将业务产生的计数增量事件异步发送到 Kafka 主题，供聚合消费者处理。</p>
 */
@Service
@Slf4j
public class CounterEventProducer {
    public static final String OUTBOX_KEY = "counter:event:outbox";
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final long sendTimeoutSeconds;

    public CounterEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                                StringRedisTemplate redis,
                                @Value("${counter.kafka-send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /**
     * 发布计数事件到 Kafka。
     * @param event 计数事件（实体类型、ID、指标、delta 等）
     */
    public void publish(CounterEvent event) {
        try {
            send(event, serialize(event));
        } catch (Exception e) {
            throw new IllegalStateException("计数事件发送失败", e);
        }
    }

    public String serialize(CounterEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("计数事件序列化失败", e);
        }
    }

    /** Redis 事实更新与事件入桶原子完成；Kafka 临时失败时由该任务持续补投。 */
    @Scheduled(fixedDelayString = "${counter.outbox-relay-delay-ms:1000}")
    public void relayPending() {
        try (Cursor<java.util.Map.Entry<Object, Object>> cursor = redis.opsForHash().scan(
                OUTBOX_KEY, ScanOptions.scanOptions().count(100).build())) {
            int sent = 0;
            while (cursor.hasNext() && sent < 100) {
                java.util.Map.Entry<Object, Object> entry = cursor.next();
                String eventId = String.valueOf(entry.getKey());
                String payload = String.valueOf(entry.getValue());
                try {
                    CounterEvent event = objectMapper.readValue(payload, CounterEvent.class);
                    send(event, payload);
                    redis.opsForHash().delete(OUTBOX_KEY, eventId);
                    sent++;
                } catch (Exception e) {
                    log.warn("Counter outbox relay paused, eventId={}", eventId, e);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Counter outbox scan failed", e);
        }
    }

    private void send(CounterEvent event, String payload) throws Exception {
        String key = event.getEntityType() + ":" + event.getEntityId();
        kafka.send(CounterTopics.EVENTS, key, payload)
                .get(sendTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }
}
