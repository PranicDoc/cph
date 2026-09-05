package com.pranicdoc.cph.rbac.controller;

import com.pranicdoc.cph.rbac.baseline.BaselineRoleBackfillReport;
import com.pranicdoc.cph.rbac.baseline.BaselineRoleBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BaselineRoleBackfillController — the one deploy-ops endpoint that provisions
 * the baseline "Practitioner" role and hands it to every existing healer that
 * has none.
 *
 * <p>Driven by {@code .github/workflows/backfill-practitioner-role.yml}: after
 * a deploy (and re-runnable standalone from the Actions tab) it logs in as a
 * SUPADM service account and POSTs here. SUPADM short-circuits
 * {@link com.pranicdoc.cph.rbac.RbacAuthz#hasPermission}, so the
 * {@code ROLES:UPDATE} gate below admits it; any other caller needs a real
 * {@code ROLES:UPDATE} grant.
 *
 * <p>Not part of the Phase-40 legacy-cutover surface or the Phase-42 healer
 * surface — recorded in {@code LegacyEndpointCutoverSweepTest}'s
 * {@code DELIBERATELY_UNSWEPT} map.
 */
@RestController
@RequestMapping("/api/admin/roles/practitioner")
@RequiredArgsConstructor
public class BaselineRoleBackfillController {

    private final BaselineRoleBackfillService baselineRoleBackfillService;

    @PostMapping("/backfill")
    @PreAuthorize("@rbacAuthz.hasPermission('ROLES','UPDATE')")
    public ResponseEntity<BaselineRoleBackfillReport> backfill(
            @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(baselineRoleBackfillService.run(dryRun));
    }
}
