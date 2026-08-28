package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.rbac.dto.RbacCatalogResourceResponse;
import com.pranicdoc.cph.rbac.service.RbacCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RbacCatalogController -- {@code GET /api/admin/roles/catalog} (38-CONTEXT D-03).
 * <p>
 * Returns, per grantable resource, a backend-owned human-readable label plus
 * its allow-listed verbs with labels. This is the single source the
 * create/edit permission-toggle grid and the resource-picker dialog consume
 * (D-02, ROLE-02 / ROLE-11) so the frontend never hardcodes the allow-list
 * and never renders a raw enum token like {@code LEGAL_DOCUMENTS}.
 * <p>
 * The catalog is only meaningful while reading or building a role, so the
 * handler carries the byte-identical combined {@code ROLES:READ} gate used by
 * {@link RbacRoleController}'s read endpoints -- a coarse
 * {@code hasAnyRole('ADMIN','SUPADM')} check ANDed with the live, DB-backed
 * {@code @rbacAuthz.hasPermission('ROLES','READ')} check. Both halves must
 * pass; the coarse role gate alone is never sufficient here.
 * <p>
 * This controller owns no business logic -- the single handler delegates
 * directly to {@link RbacCatalogService}. Enforcement is entirely the
 * greppable, reflection-testable per-handler authorization annotation.
 */
@RestController
@RequestMapping("/api/admin/roles/catalog")
@RequiredArgsConstructor
public class RbacCatalogController {

    private final RbacCatalogService rbacCatalogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM') and @rbacAuthz.hasPermission('ROLES','READ')")
    public ResponseEntity<List<RbacCatalogResourceResponse>> catalog() {
        return ResponseEntity.ok(rbacCatalogService.catalog());
    }
}
