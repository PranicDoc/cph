package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.review.stream.ReviewStreamRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends review events to every cph instance once the change that caused them has committed. With
 * Redis on (the default), the event goes out as JSON on {@link #CHANNEL} and {@link ReviewRedisRelay}
 * on each instance hands it to that instance's open streams. With Redis off (local dev, tests), or
 * when the Redis send fails, the event is delivered to this instance's streams directly.
 */
@Slf4j
@Component
public class ReviewEventPublisher {

    public static final String CHANNEL = "cph:review-events";

    private final ReviewStreamRegistry registry;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;

    @Autowired
    public ReviewEventPublisher(ReviewStreamRegistry registry, ObjectMapper objectMapper,
                                ObjectProvider<StringRedisTemplate> redis,
                                @Value("${app.review.events.redis-enabled:true}") boolean redisEnabled) {
        this(registry, objectMapper, redisEnabled ? redis.getIfAvailable() : null, redisEnabled);
    }

    ReviewEventPublisher(ReviewStreamRegistry registry, ObjectMapper objectMapper,
                         StringRedisTemplate redis, boolean redisEnabled) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.redisEnabled = redisEnabled && redis != null;
    }

    /** Sends after the current transaction commits, and never on rollback; at once when none is active. */
    public void publish(ReviewEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    /** Best effort: a live update is a convenience, so a failure here never reaches the caller. */
    private void send(ReviewEvent event) {
        if (redisEnabled) {
            try {
                redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
                return;
            } catch (JsonProcessingException | RuntimeException e) {
                log.warn("Review event {} for submission {} not sent through Redis, delivering on this "
                        + "instance only: {}", event.type(), event.submissionId(), e.toString());
            }
        }
        try {
            registry.deliver(event);
        } catch (RuntimeException e) {
            log.warn("Review event {} for submission {} not delivered: {}",
                    event.type(), event.submissionId(), e.toString());
        }
    }
}
