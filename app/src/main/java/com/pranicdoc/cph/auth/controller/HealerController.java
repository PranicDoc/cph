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
    @PreAuthorize("@rbacAuthz.hasPermission('ACCOUNTS','READ')")
    public ResponseEntity<List<HealerResponse>> getAllHealers() {
        return ResponseEntity.ok(healerService.getAllHealers());
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("@rbacAuthz.hasPermission('ACCOUNTS','UPDATE')")
    public ResponseEntity<HealerResponse> setActive(@PathVariable UUID id,
                                                    @RequestParam boolean active) {
        return ResponseEntity.ok(healerService.setActiveStatus(id, active));
    }

    /**
     * Soft-removes a healer. Removal now requires the {@code ACCOUNTS:DELETE} grant
     * (a SUPADM caller passes via the RbacAuthz short-circuit) rather than being
     * SUPADM-only. It remains a soft remove — never a hard delete; see
     * HealerServiceImpl.removeHealer. The self-vs-other ABAC scoping in
     * HealerServiceImpl.setActiveStatus is unaffected by this change.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('ACCOUNTS','DELETE')")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        healerService.removeHealer(id);
        return ResponseEntity.noContent().build();
    }
}
