package com.pranicdoc.cph.healing.controller;

import com.pranicdoc.cph.healing.dto.SessionResponse;
import com.pranicdoc.cph.healing.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/case/{caseId}")
    public ResponseEntity<SessionResponse> create(@PathVariable UUID caseId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(caseId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(sessionService.getById(id));
    }

    @GetMapping("/case/{caseId}")
    public ResponseEntity<List<SessionResponse>> getByCaseId(@PathVariable UUID caseId) {
        return ResponseEntity.ok(sessionService.getByCaseId(caseId));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<SessionResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(sessionService.complete(id));
    }
}
