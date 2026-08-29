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
 * handler carries the byte-identical bare {@code ROLES:READ} gate used by
 * {@link RbacRoleController}'s read endpoints:
 * {@code @rbacAuthz.hasPermission('ROLES','READ')}. Phase 40 D-02/D-03
 * replaced the original combined expression — a coarse {@code ADMIN}/{@code
 * SUPADM} role check ANDed with the permission call (36-CONTEXT D-05) — with
 * this bare form so the whole admin surface uses one idiom and the
 * {@code SUPADM} literal survives only inside {@code RbacAuthz.hasPermission}'s
 * short-circuit. No coarse role literal remains in the {@code rbac} package.
 * SUPADM still passes via that short-circuit; an unauthenticated caller is
 * still rejected by {@code SecurityConfig.anyRequest().authenticated()} before
 * method security, and {@code RbacAuthz} fails closed on a broken security
 * context.
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
    @PreAuthorize("@rbacAuthz.hasPermission('ROLES','READ')")
    public ResponseEntity<List<RbacCatalogResourceResponse>> catalog() {
        return ResponseEntity.ok(rbacCatalogService.catalog());
    }
}
