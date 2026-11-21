package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.review.stream.ReviewStreamRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.io.IOException;

/** Hands each review event published on {@link ReviewEventPublisher#CHANNEL} to this instance's streams. */
@Slf4j
public class ReviewRedisRelay implements MessageListener {

    private final ReviewStreamRegistry registry;
    private final ObjectMapper objectMapper;

    public ReviewRedisRelay(ReviewStreamRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        ReviewEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), ReviewEvent.class);
        } catch (IOException e) {
            log.warn("Ignoring malformed review event on {}: {}", ReviewEventPublisher.CHANNEL, e.getMessage());
            return;
        }
        registry.deliver(event);
    }
}
