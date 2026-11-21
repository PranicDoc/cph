package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.review.notify.ReviewNotifier;
import com.pranicdoc.cph.review.stream.EventStreamRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends review and PDF-ready events to every cph instance once the change that caused them has committed. With
 * Redis on (the default), the event goes out as JSON on {@link #CHANNEL} and {@link ReviewRedisRelay}
 * on each instance hands it to that instance's open streams. With Redis off (local dev, tests), or
 * when the Redis send fails, the event is delivered to this instance's streams directly.
 * Either way the instance that made the change also hands the event to {@link ReviewNotifier} (email).
 */
@Slf4j
@Component
public class ReviewEventPublisher {

    public static final String CHANNEL = "cph:review-events";

    private final EventStreamRegistry registry;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;
    private final ReviewNotifier notifier;
    private final TransactionService transactionService;

    // Hand-written: @Value constructor parameter, and the Redis template is looked up only when enabled.
    @Autowired
    public ReviewEventPublisher(EventStreamRegistry registry, ObjectMapper objectMapper,
                                ObjectProvider<StringRedisTemplate> redis,
                                @Value("${app.review.events.redis-enabled:true}") boolean redisEnabled,
                                ReviewNotifier notifier, TransactionService transactionService) {
        this(registry, objectMapper, redisEnabled ? redis.getIfAvailable() : null, redisEnabled, notifier,
                transactionService);
    }

    ReviewEventPublisher(EventStreamRegistry registry, ObjectMapper objectMapper,
                         StringRedisTemplate redis, boolean redisEnabled, ReviewNotifier notifier,
                         TransactionService transactionService) {
        this.notifier = notifier;
        this.transactionService = transactionService;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.redisEnabled = redisEnabled && redis != null;
    }

    /**
     * Sends the live event after the current transaction commits, and never on rollback; at once when none
     * is active. The email is built right here, inside the writing transaction, so its kind follows the
     * state the action just wrote; the notifier queues its delivery for after the same commit.
     */
    public void publish(ReviewEvent event) {
        notifyByEmail(event);
        transactionService.runAfterCommitOrNow(() -> send(event));
    }

    /**
     * The email goes out from the instance that made the change (the Redis relay only feeds open
     * streams), so each event is mailed once. Never throws: mail must not affect the action.
     */
    private void notifyByEmail(ReviewEvent event) {
        try {
            notifier.onEvent(event);
        } catch (RuntimeException e) {
            log.warn("Review email for {} on submission {} not queued: {}",
                    event.type(), event.submissionId(), e.toString());
        }
    }

    /**
     * Tells the healer a PDF for one of their cases is ready. Called from the pdfWorker webhook, which
     * has no transaction to wait for, so it goes out at once. No email. Same Redis fan-out as review events.
     */
    public void publishPdfReady(PdfReadyEvent event) {
        send(event);
    }

    /** Best effort: a live update is a convenience, so a failure here never reaches the caller. */
    private void send(UserEvent event) {
        if (redisEnabled) {
            try {
                redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
                return;
            } catch (JsonProcessingException | RuntimeException e) {
                log.warn("{} not sent through Redis, delivering on this instance only: {}",
                        describe(event), e.toString());
            }
        }
        try {
            registry.deliver(event);
        } catch (RuntimeException e) {
            log.warn("{} not delivered: {}", describe(event), e.toString());
        }
    }

    private static String describe(UserEvent event) {
        if (event instanceof ReviewEvent review) {
            return "Review event " + review.type() + " for submission " + review.submissionId();
        }
        if (event instanceof PdfReadyEvent pdf) {
            return "PDF-ready event for job " + pdf.jobId();
        }
        return "Event";
    }
}
