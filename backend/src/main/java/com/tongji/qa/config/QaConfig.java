package com.tongji.qa.config;

import com.tongji.qa.event.QaTopics;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 多轮问答模块配置入口：注册 {@link QaProperties}，并声明式创建 Kafka 主题。
 * <p>ChatClient / KafkaTemplate / 雪花 ID 等基础设施由其它模块全局提供，此处无需重复声明。
 */
@Configuration
@EnableConfigurationProperties(QaProperties.class)
public class QaConfig {

    /**
     * 记忆自动更新主题：每累计 N 轮对话后由生产者发布，消费者调 LLM 总结用户画像。
     * broker 允许 auto.create 时，应用启动即创建。
     */
    @Bean
    public org.apache.kafka.clients.admin.NewTopic qaMemoryUpdateTopic() {
        return TopicBuilder.name(QaTopics.MEMORY_UPDATE)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(7 * 24 * 3600 * 1000L))
                .build();
    }
}
