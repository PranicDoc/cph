package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.healing.dto.ProtocolRequest;
import com.pranicdoc.cph.healing.service.impl.ProtocolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/protocol")
@RequiredArgsConstructor
public class ProtocolController {

    private final ProtocolService protocolService;

    // Sole enforcement site for Resource.SESSION_PROTOCOL. This resource is deliberately
    // distinct from MASTER_DATA_PROTOCOLS (the admin protocol catalog) — a healer-session
    // protocol grant can never be satisfied by an admin master-data grant. CaseController's
    // protocol-understanding pair is CASE:* per D-06, so no other controller may use this
    // resource. CREATE only — this controller has one POST and nothing else.
    @PreAuthorize("@rbacAuthz.hasPermission('SESSION_PROTOCOL','CREATE')")
    @PostMapping
    public ResponseEntity<Void> saveProtocol(
            @PathVariable UUID sessionId,
            @Valid @RequestBody List<ProtocolRequest> requests) {
        protocolService.saveProtocol(sessionId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
