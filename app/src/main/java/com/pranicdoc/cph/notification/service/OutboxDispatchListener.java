package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.config.NotificationProperties;
import com.pranicdoc.cph.notification.config.NotificationProperties.DispatchMode;
import com.pranicdoc.cph.notification.event.OutboxCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

/**
 * Sends a new outbox row as soon as the transaction that wrote it commits. Never throws: if this path
 * fails or is skipped, the row stays PENDING/FAILED and {@link OutboxSweeper} picks it up.
 */
@Slf4j
@Component
public class OutboxDispatchListener {

    private final OutboxDispatcher dispatcher;
    private final Executor executor;
    private final NotificationProperties properties;

    public OutboxDispatchListener(OutboxDispatcher dispatcher,
                                  @Qualifier("whatsappWorkerExecutor") Executor executor,
                                  NotificationProperties properties) {
        this.dispatcher = dispatcher;
        this.executor = executor;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxCreated(OutboxCreatedEvent event) {
        try {
            if (properties.getDispatch() == DispatchMode.SYNC) {
                dispatchQuietly(event);
            } else {
                executor.execute(() -> dispatchQuietly(event));
            }
        } catch (Exception e) {
            log.warn("Eager dispatch not started for outbox {}; sweeper will retry: {}", event.outboxId(), e.toString());
        }
    }

    private void dispatchQuietly(OutboxCreatedEvent event) {
        try {
            dispatcher.dispatch(event.outboxId());
        } catch (Exception e) {
            log.warn("Eager dispatch failed for outbox {}; sweeper will retry: {}", event.outboxId(), e.toString());
        }
    }
}
