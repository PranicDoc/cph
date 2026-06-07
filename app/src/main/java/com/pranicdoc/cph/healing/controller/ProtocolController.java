package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.healing.dto.ProtocolRequest;
import com.pranicdoc.cph.healing.service.ProtocolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{caseId}/protocol")
@RequiredArgsConstructor
public class ProtocolController {

    private final ProtocolService protocolService;

    @PostMapping
    public ResponseEntity<Void> saveProtocol(
            @PathVariable UUID sessionId,
            @Valid @RequestBody List<ProtocolRequest> requests) {
        protocolService.saveProtocol(sessionId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
