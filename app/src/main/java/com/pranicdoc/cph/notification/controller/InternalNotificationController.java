package com.pranicdoc.cph.notification.controller;

import com.pranicdoc.cph.notification.service.OutboxSweeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Machine-to-machine trigger for the outbox sweep — called by Cloud Scheduler on Cloud Run, where the
 * in-process cron is off. permitAll in SecurityConfig; authenticated here by {@code X-Internal-Key}
 * (app.internal.api-key). With no key configured the endpoint answers 404, as if it didn't exist.
 */
@RestController
@RequestMapping("/api/v1/internal/notifications")
public class InternalNotificationController {

    static final String KEY_HEADER = "X-Internal-Key";

    private final OutboxSweeper sweeper;
    private final byte[] apiKey;

    public InternalNotificationController(OutboxSweeper sweeper,
                                          @Value("${app.internal.api-key:}") String apiKey) {
        this.sweeper = sweeper;
        this.apiKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/sweep")
    public ResponseEntity<OutboxSweeper.Result> sweep(
            @RequestHeader(value = KEY_HEADER, required = false) String key) {
        if (apiKey.length == 0) {
            return ResponseEntity.notFound().build();
        }
        if (key == null || !MessageDigest.isEqual(apiKey, key.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(sweeper.sweep());
    }
}
