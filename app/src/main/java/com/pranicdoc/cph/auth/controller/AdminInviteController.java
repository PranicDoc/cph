package com.pranicdoc.cph.auth.controller;

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
 * gated by dynamic RBAC — mirrors AdminCreditController's pattern, but calls
 * the local InviteService directly instead of proxying to a remote service.
 *
 * Solo-healer world (CUTOVER-05/D-26-02): invite visibility is no longer clinic-scoped.
 * Access to each endpoint is now decided by a {@code HEALER_REG} grant resolved through
 * {@link com.pranicdoc.cph.rbac.RbacAuthz}; a SUPADM caller passes via that bean's single
 * short-circuit without needing any grant. The one remaining static authorization
 * distinction is the invite-escalation guard inside {@link #create} (a non-SUPADM caller
 * cannot mint a SUPADM), which Phase 41 will fold into the permission model.
 */
@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
public class AdminInviteController {

    private final InviteService inviteService;
    private final SecurityContextUtil securityContextUtil;

    @PostMapping
    @PreAuthorize("@rbacAuthz.hasPermission('HEALER_REG','CREATE')")
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody InviteCreateRequest request) {
        if (request.role() == Role.SUPADM && !"SUPADM".equals(securityContextUtil.getCurrentRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a SUPADM can invite a SUPADM");
        }

        InviteResponse created = inviteService.createInvite(
                request.email(), request.role(), securityContextUtil.getCurrentHealerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("@rbacAuthz.hasPermission('HEALER_REG','READ')")
    public Page<InviteResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inviteService.list(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("@rbacAuthz.hasPermission('HEALER_REG','UPDATE')")
    public ResponseEntity<InviteResponse> resend(@PathVariable UUID id) {
        InviteResponse updated = inviteService.resend(id);
        return ResponseEntity.ok(updated);
    }

    // PD-40-07: revoke maps to HEALER_REG:UPDATE, NOT HEALER_REG:DELETE. The HEALER_REG
    // resource's allow-list is READ/CREATE/UPDATE only (no DELETE), so a DELETE pair could
    // never be written to role_permission (Permission's constructor rejects it) and the gate
    // would fail closed forever for every non-SUPADM caller. The "revoke -> DELETE" mapping
    // in docs/rbac/SUPADM-BYPASS-AND-BACKFILL.md is stale — it referenced a HEALER_REGISTRATION
    // resource that does not exist.
    @PostMapping("/{id}/revoke")
    @PreAuthorize("@rbacAuthz.hasPermission('HEALER_REG','UPDATE')")
    public ResponseEntity<InviteResponse> revoke(@PathVariable UUID id) {
        InviteResponse updated = inviteService.revoke(id);
        return ResponseEntity.ok(updated);
    }
}
