package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.common.dto.PageResponse;
import com.pranicdoc.cph.rbac.dto.RoleCreateRequest;
import com.pranicdoc.cph.rbac.dto.RoleResponse;
import com.pranicdoc.cph.rbac.dto.RoleSummaryResponse;
import com.pranicdoc.cph.rbac.dto.RoleUpdateRequest;
import com.pranicdoc.cph.rbac.service.RbacRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * RbacRoleController — the {@code /api/admin/roles} CRUD surface (RBAC-02,
 * PD-36-14, PD-36-15).
 * <p>
 * Every endpoint carries the combined D-05 authorization expression: a
 * coarse {@code hasAnyRole('ADMIN','SUPADM')} gate ANDed with a live,
 * DB-backed {@code @rbacAuthz.hasPermission('ROLES', verb)} check delegating
 * to {@link com.pranicdoc.cph.rbac.RbacAuthz} (plan 36-02). Both halves must
 * pass — the coarse role gate alone is never sufficient on this surface.
 * <p>
 * Verb mapping rationale: {@link com.pranicdoc.cph.rbac.model.Resource#ROLES}
 * supports exactly {@code READ}, {@code CREATE}, {@code UPDATE} and
 * {@code DELETE} under the current 8-verb {@code Operation} / 15-resource
 * {@code Resource} catalog (D-01). The blanket {@code 'WRITE'} literal that
 * appears in {@code docs/rbac/SUPADM-BYPASS-AND-BACKFILL.md}'s illustrative
 * sketch is stale against that catalog and must not be used here; that
 * document's reconciliation belongs to Phase 40, not this plan.
 * <p>
 * This controller owns no business logic and performs no manual permission
 * checks in a method body — enforcement is entirely the greppable,
 * reflection-testable per-handler authorization annotation, not an
 * easily-forgotten inline call. All five methods delegate directly to
 * {@link RbacRoleService}.
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RbacRoleController {

    private final RbacRoleService rbacRoleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','READ')")
    public ResponseEntity<PageResponse<RoleSummaryResponse>> list(
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(rbacRoleService.list(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','READ')")
    public ResponseEntity<RoleResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(rbacRoleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','CREATE')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rbacRoleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','UPDATE')")
    public ResponseEntity<RoleResponse> update(
            @PathVariable UUID id, @Valid @RequestBody RoleUpdateRequest request) {
        // Argument inversion at THIS call site only (PD-36-18): the service's
        // AuditAspect serializes only its first argument into the audit
        // payload, so the request DTO is passed first and the id second even
        // though this method's own REST-conventional signature stays
        // (id, request). Do not normalize this back to (id, request) at the
        // call site — that would silently drop the audit trail's ability to
        // show what changed, not just which row was touched.
        return ResponseEntity.ok(rbacRoleService.update(request, id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        rbacRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
