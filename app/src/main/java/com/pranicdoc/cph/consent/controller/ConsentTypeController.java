package com.pranicdoc.cph.consent.controller;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.consent.dto.ConsentTypeCreateRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeRenameRequest;
import com.pranicdoc.cph.consent.dto.ConsentTypeResponse;
import com.pranicdoc.cph.consent.service.ConsentTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for the {@code consent_type} registry (CONSENT-09/10/11,
 * Phase 31.1). Unlike sibling {@link ConsentController} — whose endpoints
 * are deliberately self-service and ungated — every endpoint here carries
 * an admin-only role gate ({@code hasAnyRole('ADMIN', 'SUPADM')}), including
 * the GET list, because the registry exposes internal keys and compliance
 * flags (T-31.1-03).
 *
 * <p>Hard delete is intentionally absent from this resource (D-02) —
 * deactivation via {@code PATCH .../active} is the only removal-adjacent
 * operation, and it never touches the inherited row-removal mechanism.
 *
 * <p>{@code isSuperAdmin} is resolved here, server-side, from
 * {@link SecurityContextUtil#getCurrentRole()} on the authenticated
 * principal — it is never accepted as a request parameter, header, or body
 * field, so a caller cannot self-elevate the D-07 system-row guard by
 * crafting a request (T-31.1-01).
 */
@RestController
@RequestMapping("/api/v1/consents/types")
@RequiredArgsConstructor
public class ConsentTypeController {

    private final ConsentTypeService consentTypeService;
    private final SecurityContextUtil securityContextUtil;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<List<ConsentTypeResponse>> list(
            @RequestParam(value = "includeInactive", required = false, defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(consentTypeService.list(includeInactive));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ConsentTypeResponse> create(@Valid @RequestBody ConsentTypeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consentTypeService.create(request));
    }

    @PatchMapping("/{id}/label")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ConsentTypeResponse> rename(
            @PathVariable UUID id, @Valid @RequestBody ConsentTypeRenameRequest request) {
        return ResponseEntity.ok(consentTypeService.rename(id, request));
    }

    @PatchMapping("/{id}/mandatory")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ConsentTypeResponse> setMandatory(
            @PathVariable UUID id, @RequestParam("value") boolean value) {
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        return ResponseEntity.ok(consentTypeService.setMandatory(id, value, isSuperAdmin));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPADM')")
    public ResponseEntity<ConsentTypeResponse> setActive(
            @PathVariable UUID id, @RequestParam("value") boolean value) {
        boolean isSuperAdmin = "SUPADM".equals(securityContextUtil.getCurrentRole());
        return ResponseEntity.ok(consentTypeService.setActive(id, value, isSuperAdmin));
    }
}
