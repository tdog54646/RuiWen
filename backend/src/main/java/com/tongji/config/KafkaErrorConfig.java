package com.tongji.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka 消费异常的统一处理：失败重试（指数退避）+ 死信队列（DLT）。
 *
 * <p>任何 @KafkaListener 抛出异常后：
 * <ol>
 *   <li>按指数退避重试 N 次 —— 临时故障（拉正文网络抖动、embedding 偶发超时）可自愈；</li>
 *   <li>重试仍失败，把消息发往死信 topic（&lt;原topic&gt;.DLT），消息完整保留，consumer 继续前进；</li>
 *   <li>DLT 里的消息可单独排查/修复后重放，配合全量重建兜底，保证最终一致。</li>
 * </ol>
 *
 * <p>替代各 listener 内部 {@code catch(Exception ignored)} 吞异常的旧做法，避免单条 poison message
 * 卡死整条消费链路、后续消息全部积压。
 *
 * <p>Spring Boot 3.x 会自动把名为 {@code kafkaErrorHandler} 的 {@code CommonErrorHandler} Bean
 * 注入到默认的 {@code kafkaListenerContainerFactory}，对所有 @KafkaListener 生效。
 */
@Configuration
public class KafkaErrorConfig {

    @Bean
    @SuppressWarnings("unchecked")
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
        // 失败后发到死信 topic：默认命名 <原topic>.DLT，分区与原记录一致。
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer((KafkaTemplate<Object, Object>) template);
        // 指数退避：1s -> 2s -> 4s ...，总时长上限 8s（约 3 次重试）
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);
        backOff.setMaxElapsedTime(8_000L);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
