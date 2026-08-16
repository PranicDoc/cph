package com.pranicdoc.cph.consent.controller;

import com.pranicdoc.cph.consent.dto.ReadingSessionCloseRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionOpenRequest;
import com.pranicdoc.cph.consent.dto.ReadingSessionResponse;
import com.pranicdoc.cph.consent.service.ReadingSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST surface for the reading-session capability (READ-01/READ-02). No
 * role-restriction annotation is applied here — every endpoint is
 * self-service, scoped to the authenticated healer inside
 * {@link ReadingSessionService} via {@code SecurityContextUtil} (same posture
 * as {@code ConsentController}). Authentication itself is already enforced
 * globally by SecurityConfig's {@code .anyRequest().authenticated()} gate;
 * these paths are never added to a permitAll list (T-33-10).
 *
 * <p>The close endpoint answers to both PATCH and POST on the same URL because
 * {@code navigator.sendBeacon()} can only issue POST, so a PATCH-only route
 * would silently 405 every abandon-without-clicking case (Pitfall 2) — a
 * future reader should not "tidy" this into a PATCH-only mapping.
 */
@RestController
@RequestMapping("/api/v1/consents/reading-sessions")
@RequiredArgsConstructor
public class ReadingSessionController {

    private final ReadingSessionService readingSessionService;

    @PostMapping
    public ResponseEntity<ReadingSessionResponse> open(@Valid @RequestBody ReadingSessionOpenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(readingSessionService.open(request));
    }

    @RequestMapping(value = "/{id}/close", method = { RequestMethod.PATCH, RequestMethod.POST })
    public ResponseEntity<ReadingSessionResponse> close(@PathVariable UUID id,
                                                          @RequestBody(required = false) ReadingSessionCloseRequest request) {
        return ResponseEntity.ok(readingSessionService.close(id, request));
    }
}
