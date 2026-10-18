package com.pranicdoc.cph.notification.controller;

import com.pranicdoc.cph.notification.service.OutboxSweeper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-to-machine trigger for the outbox sweep — called by Cloud Scheduler on Cloud Run, where the
 * in-process cron is off. Authentication is done before this controller by InternalApiKeyFilter
 * (InternalApiSecurityConfig's filter chain).
 */
@RestController
@RequestMapping("/api/v1/internal/notifications")
public class InternalNotificationController {

    private final OutboxSweeper sweeper;

    public InternalNotificationController(OutboxSweeper sweeper) {
        this.sweeper = sweeper;
    }

    @PostMapping("/sweep")
    public ResponseEntity<OutboxSweeper.Result> sweep() {
        return ResponseEntity.ok(sweeper.sweep());
    }
}
