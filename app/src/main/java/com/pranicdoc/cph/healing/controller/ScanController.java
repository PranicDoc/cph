package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.healing.dto.ScanRequest;
import com.pranicdoc.cph.healing.service.impl.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/scans")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    // Sole enforcement site for Resource.SCAN. SCAN allow-lists CREATE only because the only
    // scan-read path, SessionController.getAllScannedChakras, gates SESSION:READ per D-03, so
    // SCAN has no read verb this phase. The current cph/ui wizard bundles scans into
    // POST /sessions/case rather than calling this endpoint directly; it is gated anyway
    // because SC1/D-16 require every request-mapped healer method to carry a gate, not only
    // the ones the current UI happens to call.
    @PreAuthorize("@rbacAuthz.hasPermission('SCAN','CREATE')")
    @PostMapping
    public ResponseEntity<Void> saveScan(@PathVariable UUID sessionId,
                                          @Valid @RequestBody ScanRequest request) {
        scanService.saveScan(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
