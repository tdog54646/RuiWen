package com.tongji.knowpost.listener;

import com.tongji.knowpost.service.FeedCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedPublicCacheRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(FeedPublicCacheRedisSubscriber.class);

    private final FeedCacheService feedCacheService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            log.debug("收到清缓存信息");
            feedCacheService.deletePublicFeedLocalCaches();
        } catch (Exception e) {
            log.warn("Failed to invalidate public feed caches from redis pubsub: {}", e.getMessage());
        }
    }
}

@Configuration
@RequiredArgsConstructor
class FeedPublicCacheRedisSubscriberConfig {

    private final RedisConnectionFactory redisConnectionFactory;
    private final FeedPublicCacheRedisSubscriber feedPublicCacheRedisSubscriber;

    @Bean
    public RedisMessageListenerContainer knowPostFeedCacheRedisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
                feedPublicCacheRedisSubscriber,
                new ChannelTopic(FeedCacheService.PUBLIC_FEED_CACHE_INVALIDATE_CHANNEL)
        );
        return container;
    }
}
