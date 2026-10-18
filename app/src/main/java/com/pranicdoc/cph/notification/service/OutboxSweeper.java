package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.config.NotificationProperties;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Safety net for the eager path: dead-letters rows stuck in PROCESSING with no retries left, then
 * re-sends (synchronously, one at a time) anything PENDING past the grace period, FAILED, or stuck.
 * Triggered every 30 min by {@link OutboxSweepScheduler} (VM) or the internal endpoint (Cloud Run).
 */
@Slf4j
@Service
public class OutboxSweeper {

    public record Result(int deadLettered, int attempted, int sent) {
    }

    private final OutboxRepository repository;
    private final OutboxDispatcher dispatcher;
    private final TransactionTemplate tx;
    private final NotificationProperties properties;

    public OutboxSweeper(OutboxRepository repository, OutboxDispatcher dispatcher,
                         TransactionTemplate tx, NotificationProperties properties) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.tx = tx;
        this.properties = properties;
    }

    public Result sweep() {
        Instant now = Instant.now();
        Instant stuckBefore = now.minus(properties.getStuckAfter());
        int maxRetries = properties.getMaxRetries();

        Integer deadLettered = tx.execute(status -> repository.markExhaustedStuckAsDlq(now, stuckBefore, maxRetries));
        List<UUID> candidates = repository.findSweepCandidates(
                now.minus(properties.getPendingGrace()), stuckBefore, maxRetries, properties.getSweep().getBatchSize());

        int sent = 0;
        for (UUID id : candidates) {
            try {
                if (dispatcher.dispatch(id)) {
                    sent++;
                }
            } catch (Exception e) {
                log.warn("Sweep could not dispatch outbox {}: {}", id, e.toString());
            }
        }

        Result result = new Result(deadLettered == null ? 0 : deadLettered, candidates.size(), sent);
        if (result.deadLettered() > 0 || result.attempted() > 0) {
            log.info("Outbox sweep: {}", result);
        }
        return result;
    }
}
