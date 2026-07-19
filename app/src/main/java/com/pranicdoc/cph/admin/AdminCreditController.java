package com.pranicdoc.cph.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Admin proxy controller — forwards requests to creditService with RBAC + ABAC enforced.
 *
 * Solo-healer world (D-26-02): the only remaining privilege tier is SUPADM
 * (cross-healer, unscoped) vs. an ordinary healer (own healerId only). There
 * is no clinic-scoped ADMIN tier — that concept no longer exists.
 *
 * RBAC layers:
 *   @PreAuthorize("hasRole('SUPADM')")        — packages + global config (SUPADM only)
 *   @PreAuthorize("hasAnyRole('ADMIN','SUPADM')") — accounts, transactions
 *
 * ABAC:
 *   AdminAuthz.assertHealerAccess(healerId)   — an ordinary healer can only touch their own account
 *   AdminAuthz.healerScopeFilter()            — scopes list queries for a non-SUPADM caller to their own healerId
 *
 * The X-Api-Key is added by creditServiceRestClient default header (never exposed to browser).
 * Request bodies are forwarded verbatim as String to avoid coupling CPH to creditService DTOs.
 */
@RestController
@RequestMapping("/api/admin/credit")
@RequiredArgsConstructor
public class AdminCreditController {

    @Qualifier("creditServiceRestClient")
    private final RestClient creditServiceRestClient;

    private final AdminAuthz adminAuthz;

    // ─── Packages (SUPADM only) ───────────────────────────────────────────────

    @GetMapping("/packages")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<String> listAllPackages() {
        String body = creditServiceRestClient.get()
                .uri("/credits/admin/packages")
                .retrieve()
                .body(String.class);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/packages")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<String> createPackage(@RequestBody String body) {
        return ResponseEntity.ok(creditServiceRestClient.post()
                .uri("/credits/admin/packages")
                .body(body)
                .retrieve()
                .body(String.class));
    }

    @PutMapping("/packages/{id}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<String> updatePackage(@PathVariable UUID id, @RequestBody String body) {
        return ResponseEntity.ok(creditServiceRestClient.put()
                .uri("/credits/admin/packages/{id}", id)
                .body(body)
                .retrieve()
                .body(String.class));
    }

    @DeleteMapping("/packages/{id}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<String> deactivatePackage(@PathVariable UUID id) {
        return ResponseEntity.ok(creditServiceRestClient.delete()
                .uri("/credits/admin/packages/{id}", id)
                .retrieve()
                .body(String.class));
    }

    // ─── Global config (SUPADM only) ─────────────────────────────────────────

    @GetMapping("/config")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<String> listConfig() {
        return ResponseEntity.ok(creditServiceRestClient.get()
                .uri("/credits/admin/config")
                .retrieve()
                .body(String.class));
    }

    @PutMapping("/config/{key}")
    @PreAuthorize("hasRole('SUPADM')")
    public ResponseEntity<String> updateConfig(@PathVariable String key, @RequestBody String body) {
        return ResponseEntity.ok(creditServiceRestClient.put()
                .uri("/credits/admin/config/{key}", key)
                .body(body)
                .retrieve()
                .body(String.class));
    }

    // ─── Accounts (ADMIN + SUPADM, ABAC scoped) ──────────────────────────────

    /**
     * List accounts. An ordinary healer sees only their own HEALER account.
     * SUPADM sees all (no ownerType/ownerId filter injected).
     */
    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public ResponseEntity<String> listAccounts(
            @RequestParam(required = false) String ownerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID healerFilter = adminAuthz.healerScopeFilter();

        String uri;
        if (healerFilter != null) {
            // Ordinary healer: scope to their own HEALER account only
            uri = "/credits/admin/accounts?ownerType=HEALER&ownerId=" + healerFilter
                    + "&page=" + page + "&size=" + size;
        } else {
            // SUPADM: pass caller's filter (or no filter)
            uri = "/credits/admin/accounts"
                    + "?page=" + page + "&size=" + size
                    + (ownerType != null ? "&ownerType=" + ownerType : "");
        }

        return ResponseEntity.ok(creditServiceRestClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class));
    }

    // ─── Transactions (ADMIN + SUPADM, ABAC scoped) ──────────────────────────

    @GetMapping("/accounts/{ownerType}/{ownerId}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public ResponseEntity<String> getTransactions(
            @PathVariable String ownerType,
            @PathVariable UUID ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        enforceOwnerAccess(ownerType, ownerId);
        return ResponseEntity.ok(creditServiceRestClient.get()
                .uri("/credits/transactions?ownerType={ot}&ownerId={id}&page={p}&size={s}",
                        ownerType, ownerId, page, size)
                .retrieve()
                .body(String.class));
    }

    // ─── Manual adjustment (ADMIN + SUPADM, ABAC scoped) ─────────────────────

    @PostMapping("/accounts/{ownerType}/{ownerId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public ResponseEntity<String> adjust(
            @PathVariable String ownerType,
            @PathVariable UUID ownerId,
            @RequestBody String body) {
        enforceOwnerAccess(ownerType, ownerId);
        return ResponseEntity.ok(creditServiceRestClient.post()
                .uri("/credits/adjust")
                .body(body)
                .retrieve()
                .body(String.class));
    }

    // ─── PAYG toggle (ADMIN + SUPADM, ABAC scoped) ───────────────────────────

    @PutMapping("/accounts/{ownerType}/{ownerId}/payg")
    @PreAuthorize("hasAnyRole('ADMIN','SUPADM')")
    public ResponseEntity<String> togglePayg(
            @PathVariable String ownerType,
            @PathVariable UUID ownerId,
            @RequestBody String body) {
        enforceOwnerAccess(ownerType, ownerId);
        return ResponseEntity.ok(creditServiceRestClient.put()
                .uri("/credits/accounts/payg")
                .body(body)
                .retrieve()
                .body(String.class));
    }

    // ─── ABAC helper ─────────────────────────────────────────────────────────

    private void enforceOwnerAccess(String ownerType, UUID ownerId) {
        if ("HEALER".equalsIgnoreCase(ownerType)) {
            adminAuthz.assertHealerAccess(ownerId);
        }
    }
}
