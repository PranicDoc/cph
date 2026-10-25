package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.healing.dto.CreateSessionRequest;
import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.dto.UpdateSessionRequest;
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

    /**
     * Create a healing session for a case. This is the cph/ui wizard's real write path:
     * it bundles scan and session-protocol sub-writes that {@code SessionService.create}
     * performs inline. It is gated {@code SESSION:CREATE} <b>only</b> — a caller holding
     * {@code SESSION:CREATE} without {@code SCAN:CREATE} / {@code SESSION_PROTOCOL:CREATE}
     * still writes those sub-records through this endpoint.
     *
     * <p><b>Deliberate (WR-02 ruling, 2026-09-02).</b> Enforcing the two sub-capabilities
     * in the service layer was rejected for three reasons: (1) it would move enforcement
     * off the annotation surface the whole Phase-42 contract (SC1,
     * {@code HealerControllerGateSweepTest}, every {@code *AuthorizationContractTest},
     * RBAC-09) is built on; (2) it would entangle the change with the pre-existing red
     * {@code SessionServiceTest} baseline class, putting SC5's zero-regression evidence
     * at risk for a change with no live effect; (3) it would 403 the primary wizard flow
     * the moment a baseline Practitioner role is seeded, and role seeding is explicitly
     * out of scope per ROADMAP &sect;Phase 42. The standalone {@code ScanController.saveScan}
     * and {@code ProtocolController.saveProtocol} gates give both resources a real
     * enforcement site; only this bundled path is inert, and that is recorded here, on
     * {@code Resource.SCAN} / {@code Resource.SESSION_PROTOCOL}, and as deferred item
     * {@code D-42-14-01}. Do NOT add a second {@code @PreAuthorize} here and do NOT
     * enforce sub-capabilities in {@code SessionService}.
     */
    @PreAuthorize("@rbacAuthz.hasPermission('SESSION','CREATE')")
    @PostMapping("/case")
    public ResponseEntity<SessionResponse> create(
            @Valid @RequestBody CreateSessionRequest sessionRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(sessionRequest, idempotencyKey));
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

    @PreAuthorize("@rbacAuthz.hasPermission('SESSION','UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<SessionResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateSessionRequest request) {
        return ResponseEntity.ok(sessionService.update(id, request));
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
