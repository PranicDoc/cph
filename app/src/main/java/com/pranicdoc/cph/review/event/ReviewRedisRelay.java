package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.review.stream.EventStreamRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.io.IOException;

/** Hands each user event published on {@link ReviewEventPublisher#CHANNEL} to this instance's streams. */
@Slf4j
@RequiredArgsConstructor
public class ReviewRedisRelay implements MessageListener {

    private final EventStreamRegistry registry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        UserEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), UserEvent.class);
        } catch (IOException e) {
            log.warn("Ignoring malformed review event on {}: {}", ReviewEventPublisher.CHANNEL, e.getMessage());
            return;
        }
        registry.deliver(event);
    }
}
