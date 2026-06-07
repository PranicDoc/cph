package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.healing.dto.ScanRequest;
import com.pranicdoc.cph.healing.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{caseId}/scans")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    @PostMapping
    public ResponseEntity<Void> saveScan(@PathVariable UUID sessionId,
                                          @Valid @RequestBody ScanRequest request) {
        scanService.saveScan(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
