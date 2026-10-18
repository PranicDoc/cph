package com.pranicdoc.cph.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.notifications.*} — see docs/superpowers/specs/2026-10-18-outbox-eager-dispatch.md. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    public enum DispatchMode {
        /** Hand off to whatsappWorkerExecutor after commit — the request doesn't wait (VM). */
        ASYNC,
        /** Send inline after commit — required on Cloud Run, which freezes CPU once the response is sent. */
        SYNC
    }

    private DispatchMode dispatch = DispatchMode.ASYNC;
    private int maxRetries = 3;
    /** A PENDING row younger than this is assumed to still be on the eager path; the sweeper leaves it. */
    private Duration pendingGrace = Duration.ofMinutes(2);
    /** A PROCESSING row untouched for this long is assumed abandoned (instance died mid-send). */
    private Duration stuckAfter = Duration.ofMinutes(10);
    private Sweep sweep = new Sweep();

    @Getter
    @Setter
    public static class Sweep {
        /** In-process cron trigger. Off on Cloud Run, where Cloud Scheduler calls the internal endpoint. */
        private boolean schedulerEnabled = true;
        private String cron = "0 */30 * * * *";
        private int batchSize = 100;
    }
}
