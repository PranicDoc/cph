package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.auth.dto.HealerResponse;
import com.pranicdoc.cph.auth.service.HealerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/healers")
@RequiredArgsConstructor
public class HealerController {

    private final HealerService healerService;

    @GetMapping
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<List<HealerResponse>> getAllHealers() {
        return ResponseEntity.ok(healerService.getAllHealers());
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<HealerResponse> setActive(@PathVariable UUID id,
                                                    @RequestParam boolean active) {
        return ResponseEntity.ok(healerService.setActiveStatus(id, active));
    }

    /**
     * Soft-removes a healer (SUPADM only — more sensitive than deactivation).
     * Never a hard delete; see HealerServiceImpl.removeHealer.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        healerService.removeHealer(id);
        return ResponseEntity.noContent().build();
    }
}
