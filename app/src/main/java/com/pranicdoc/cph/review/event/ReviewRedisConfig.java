package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.review.stream.ReviewStreamRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Subscribes this instance to {@link ReviewEventPublisher#CHANNEL} on the autoconfigured
 * {@link RedisConnectionFactory}, so a review event raised on any instance reaches streams open
 * on this one. Off when {@code app.review.events.redis-enabled=false} (local dev without Redis,
 * tests); the publisher then delivers in-process.
 */
@Configuration
@ConditionalOnProperty(name = "app.review.events.redis-enabled", havingValue = "true", matchIfMissing = true)
public class ReviewRedisConfig {

    @Bean
    public ReviewRedisRelay reviewRedisRelay(ReviewStreamRegistry registry, ObjectMapper objectMapper) {
        return new ReviewRedisRelay(registry, objectMapper);
    }

    @Bean
    public RedisMessageListenerContainer reviewEventListenerContainer(RedisConnectionFactory connectionFactory,
                                                                      ReviewRedisRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new ChannelTopic(ReviewEventPublisher.CHANNEL));
        return container;
    }
}
