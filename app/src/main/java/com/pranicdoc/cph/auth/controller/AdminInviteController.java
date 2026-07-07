package com.pranicdoc.cph.auth.controller;

import com.pranicdoc.cph.admin.AdminAuthz;
import com.pranicdoc.cph.auth.dto.InviteCreateRequest;
import com.pranicdoc.cph.auth.dto.InviteResponse;
import com.pranicdoc.cph.auth.service.impl.InviteService;
import com.pranicdoc.cph.common.enums.Role;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Admin invite-lifecycle controller (ADMIN-01): create/list/resend/revoke,
 * RBAC (ADMIN/SUPADM) + ABAC (AdminAuthz clinic scoping) — mirrors
 * AdminCreditController's pattern, but calls the local InviteService
 * directly instead of proxying to a remote service.
 *
 * RBAC: every endpoint requires role ADMIN or SUPADM (method-security annotation below).
 * ABAC: AdminAuthz.effectiveClinicFilter() (null = SUPADM, own clinicId = ADMIN)
 *       scopes create/list/resend/revoke; assertClinicAccess() gates SUPADM's
 *       explicit clinicId choice on create.
 */
@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteService inviteService;
    private final AdminAuthz adminAuthz;
    private final SecurityContextUtil securityContextUtil;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody InviteCreateRequest request) {
        if (request.role() == Role.SUPADM && !"SUPADM".equals(securityContextUtil.getCurrentRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a SUPADM can invite a SUPADM");
        }

        UUID filter = adminAuthz.effectiveClinicFilter();
        UUID clinicId;
        if (filter != null) {
            // ADMIN: clinic is inferred, body clinicId ignored (D-02)
            clinicId = filter;
        } else {
            // SUPADM: clinic must be explicitly supplied (D-03)
            if (request.clinicId() == null) {
                throw new IllegalArgumentException("clinicId is required for SUPADM invites");
            }
            adminAuthz.assertClinicAccess(request.clinicId());
            clinicId = request.clinicId();
        }

        InviteResponse created = inviteService.createInvite(
                request.email(), request.role(), clinicId, securityContextUtil.getCurrentHealerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public Page<InviteResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID clinicFilter = adminAuthz.effectiveClinicFilter();
        return inviteService.list(clinicFilter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public ResponseEntity<InviteResponse> resend(@PathVariable UUID id) {
        InviteResponse updated = inviteService.resend(id, adminAuthz.effectiveClinicFilter());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public ResponseEntity<InviteResponse> revoke(@PathVariable UUID id) {
        InviteResponse updated = inviteService.revoke(id, adminAuthz.effectiveClinicFilter());
        return ResponseEntity.ok(updated);
    }
}
