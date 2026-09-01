package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.service.impl.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PreAuthorize("@rbacAuthz.hasPermission('SESSION','CREATE')")
    @PostMapping("/case")
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest sessionRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(sessionRequest));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('SESSION','READ')")
    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(sessionService.getById(id));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('SESSION','READ')")
    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<SessionResponse>> getByCaseId(@PathVariable UUID caseId) {
        return ResponseEntity.ok(sessionService.getByCaseId(caseId));
    }

    @PreAuthorize("@rbacAuthz.hasPermission('SESSION','UPDATE')")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<SessionResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(sessionService.complete(id));
    }

    // D-03: returns the Set<String> of chakra names scanned within a session — it hangs off
    // the /sessions/{id}/* aggregate and returns session-detail, so it READS THE SESSION.
    // Deliberately NOT SCAN:READ — which is precisely why Resource.SCAN allow-lists CREATE only.
    @PreAuthorize("@rbacAuthz.hasPermission('SESSION','READ')")
    @GetMapping("/{id}/chakras")
    public ResponseEntity<ApiResponse<Set<String>>> getAllScannedChakras(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(sessionService.getAllScannedChakra(id)));
    }
}
