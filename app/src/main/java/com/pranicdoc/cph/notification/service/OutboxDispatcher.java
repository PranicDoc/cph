package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.config.NotificationProperties;
import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.entity.NotificationOutbox.NotificationStatus;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Sends one outbox row: claim it (atomic, see {@link OutboxRepository#claim}), call WhatsApp with no
 * transaction open, then record the outcome. Safe to call concurrently for the same id from the
 * eager path and the sweeper — only the claimer sends.
 */
@Slf4j
@Service
public class OutboxDispatcher {

    private final OutboxRepository repository;
    private final WhatsappSender sender;
    private final TransactionTemplate tx;
    private final NotificationProperties properties;

    public OutboxDispatcher(OutboxRepository repository, WhatsappSender sender,
                            TransactionTemplate tx, NotificationProperties properties) {
        this.repository = repository;
        this.sender = sender;
        this.tx = tx;
        this.properties = properties;
    }

    /** @return true if this call sent the message. */
    public boolean dispatch(UUID id) {
        Instant now = Instant.now();
        Integer claimed = tx.execute(status ->
                repository.claim(id, now, now.minus(properties.getStuckAfter()), properties.getMaxRetries()));
        if (claimed == null || claimed == 0) {
            return false;
        }

        NotificationOutbox row = tx.execute(status -> repository.findById(id).orElse(null));
        if (row == null) {
            return false;
        }

        boolean sent = send(row);
        tx.executeWithoutResult(status -> recordOutcome(id, sent));
        return sent;
    }

    private boolean send(NotificationOutbox row) {
        try {
            sender.send(row.getPhoneNumber(), row.getPayload());
            return true;
        } catch (Exception e) {
            // No phone number or payload in logs — both are patient data.
            log.warn("WhatsApp send failed for outbox {} (attempt {}): {}", row.getId(), row.getRetryCount() + 1, e.toString());
            return false;
        }
    }

    private void recordOutcome(UUID id, boolean sent) {
        repository.findById(id).ifPresent(row -> {
            if (sent) {
                row.setStatus(NotificationStatus.PROCESSED);
            } else {
                row.setRetryCount(row.getRetryCount() + 1);
                row.setStatus(row.getRetryCount() >= properties.getMaxRetries()
                        ? NotificationStatus.DLQ
                        : NotificationStatus.FAILED);
            }
            repository.save(row);
        });
    }
}
