package com.tongji.leaderboard.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.event.CounterEvent;
import com.tongji.counter.event.CounterTopics;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.leaderboard.service.LeaderboardWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 排行榜分数变更事件 Kafka 消费者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreChangedConsumer {

    private static final String METRIC_LIKE = "like";

    private final ObjectMapper objectMapper;
    private final KnowPostMapper knowPostMapper;
    private final LeaderboardWriteService leaderboardWriteService;

    /**
     * 消费事件并调用写链路；成功后手动 ack。
     */
    @KafkaListener(topics = CounterTopics.EVENTS, groupId = "leaderboard-write")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            CounterEvent event = objectMapper.readValue(message, CounterEvent.class);
            if (!METRIC_LIKE.equals(event.getMetric())) {
                ack.acknowledge();
                return;
            }

            long postId;
            try {
                postId = Long.parseLong(event.getEntityId());
            } catch (NumberFormatException ex) {
                log.warn("Skip counter event due to invalid entityId, message={}", message);
                ack.acknowledge();
                return;
            }
            KnowPost post = knowPostMapper.findById(postId);
            if (post == null || post.getCreatorId() == null) {
                log.warn("Skip counter event because post missing owner, postId={}, message={}", postId, message);
                ack.acknowledge();
                return;
            }
            long ownerId = post.getCreatorId();

            String rankName = "like:daily:" + LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.BASIC_ISO_DATE);
            String eventId = UUID.nameUUIDFromBytes(message.getBytes(StandardCharsets.UTF_8)).toString();
            leaderboardWriteService.onCounterEvent(eventId, ownerId, rankName, event.getDelta());
            ack.acknowledge();
        } catch (Exception ex) {
            log.warn("Consume counter event failed, message={}", message, ex);
        }
    }
}
