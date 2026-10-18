package com.pranicdoc.cph.notification.event;

import java.util.UUID;

/** Published when a notification_outbox row is written; handled after the surrounding transaction commits. */
public record OutboxCreatedEvent(UUID outboxId) {
}
