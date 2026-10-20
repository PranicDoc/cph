package com.pranicdoc.cph.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** In-process sweep trigger for always-on hosts (VM). Disabled on Cloud Run, where Cloud Scheduler calls the internal endpoint. */
@Component
@ConditionalOnProperty(name = "app.notifications.sweep.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSweepScheduler {

    private final OutboxSweeper sweeper;

    public OutboxSweepScheduler(OutboxSweeper sweeper) {
        this.sweeper = sweeper;
    }

    @Scheduled(cron = "${app.notifications.sweep.cron:0 */30 * * * *}")
    public void sweep() {
        sweeper.sweep();
    }
}
