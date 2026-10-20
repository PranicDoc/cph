package com.pranicdoc.cph.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin proxy controller — forwards requests to creditService with RBAC + ABAC enforced.
 *
 * Solo-healer world (D-26-02): the only remaining privilege tier is SUPADM
 * (cross-healer, unscoped) vs. an ordinary healer (own healerId only). There
 * is no clinic-scoped ADMIN tier — that concept no longer exists.
 *
 * RBAC layers (Phase 40 cutover, D-01/D-02):
 *   Every endpoint carries a bare dynamic gate — a {@code @PreAuthorize} whose
 *   sole expression is {@code @rbacAuthz.hasPermission('RESOURCE','VERB')} with
 *   no {@code hasRole} / {@code hasAnyRole} prefix and no boolean AND. SUPADM
 *   passes every gate via the single {@code "SUPADM".equals(...)} short-circuit
 *   inside {@link com.pranicdoc.cph.rbac.RbacAuthz}; every other caller passes
 *   only if granted the named (resource, verb) pair. No {@code ADMIN} role
 *   literal remains in this file — the packages/config gates map to
 *   {@code PACKAGES} / {@code GLOBAL_CONFIG}, the account gates to
 *   {@code CREDIT_ACCOUNTS} / {@code CREDIT_ACCOUNT_TRANSACTIONS}. Authentication
 *   is still enforced by {@code SecurityConfig}'s {@code .anyRequest().authenticated()}.
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
    private final HealerRepository healerRepository;
    private final ObjectMapper objectMapper;

    // ─── Packages (SUPADM only) ───────────────────────────────────────────────

    @GetMapping("/packages")
    @PreAuthorize("@rbacAuthz.hasPermission('PACKAGES','READ')")
    public ResponseEntity<String> listAllPackages() {
        String body = creditServiceRestClient.get()
                .uri("/credits/admin/packages")
                .retrieve()
                .body(String.class);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/packages")
    @PreAuthorize("@rbacAuthz.hasPermission('PACKAGES','CREATE')")
    public ResponseEntity<String> createPackage(@RequestBody String body) {
        return ResponseEntity.ok(creditServiceRestClient.post()
                .uri("/credits/admin/packages")
                .body(body)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, (req, resp) -> {
                    throw UpstreamErrors.toException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                            objectMapper);
                })
                .body(String.class));
    }

    @PutMapping("/packages/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('PACKAGES','UPDATE')")
    public ResponseEntity<String> updatePackage(@PathVariable UUID id, @RequestBody String body) {
        return ResponseEntity.ok(creditServiceRestClient.put()
                .uri("/credits/admin/packages/{id}", id)
                .body(body)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, (req, resp) -> {
                    throw UpstreamErrors.toException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                            objectMapper);
                })
                .body(String.class));
    }

    // Hard delete; creditService answers 409 while an account is on the plan (disable it instead).
    // Enable/disable is the PUT above with {"active": true|false}.
    @DeleteMapping("/packages/{id}")
    @PreAuthorize("@rbacAuthz.hasPermission('PACKAGES','DELETE')")
    public ResponseEntity<String> deletePackage(@PathVariable UUID id) {
        return ResponseEntity.ok(creditServiceRestClient.delete()
                .uri("/credits/admin/packages/{id}", id)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, (req, resp) -> {
                    throw UpstreamErrors.toException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                            objectMapper);
                })
                .body(String.class));
    }

    // ─── Global config (SUPADM only) ─────────────────────────────────────────

    @GetMapping("/config")
    @PreAuthorize("@rbacAuthz.hasPermission('GLOBAL_CONFIG','READ')")
    public ResponseEntity<String> listConfig() {
        return ResponseEntity.ok(creditServiceRestClient.get()
                .uri("/credits/admin/config")
                .retrieve()
                .body(String.class));
    }

    // updateConfig uses UPDATE, not WRITE — GLOBAL_CONFIG does not support WRITE.
    @PutMapping("/config/{key}")
    @PreAuthorize("@rbacAuthz.hasPermission('GLOBAL_CONFIG','UPDATE')")
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
    @PreAuthorize("@rbacAuthz.hasPermission('CREDIT_ACCOUNTS','READ')")
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

        String body = creditServiceRestClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);

        return ResponseEntity.ok(withHealerNames(body));
    }

    /**
     * creditService only knows owner_id (a UUID); it has no visibility into healer
     * identity. Inject an owner_name field for HEALER-type accounts so the admin UI
     * doesn't have to show raw UUIDs. Best-effort — falls back to the raw body if the
     * upstream shape ever changes, since name display isn't critical to the accounts list.
     */
    private String withHealerNames(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                return body;
            }

            Set<UUID> healerIds = new HashSet<>();
            for (JsonNode account : content) {
                if ("HEALER".equalsIgnoreCase(account.path("owner_type").asText())) {
                    healerIds.add(UUID.fromString(account.path("owner_id").asText()));
                }
            }
            if (healerIds.isEmpty()) {
                return body;
            }

            Map<UUID, String> namesById = healerRepository.findAllById(healerIds).stream()
                    .collect(Collectors.toMap(Healer::getId, Healer::getFullName));

            for (JsonNode account : content) {
                if (account instanceof ObjectNode accountNode
                        && "HEALER".equalsIgnoreCase(account.path("owner_type").asText())) {
                    UUID ownerId = UUID.fromString(account.path("owner_id").asText());
                    accountNode.put("owner_name", namesById.get(ownerId));
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return body;
        }
    }

    // ─── Transactions (ADMIN + SUPADM, ABAC scoped) ──────────────────────────

    @GetMapping("/accounts/{ownerType}/{ownerId}/transactions")
    @PreAuthorize("@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','READ')")
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

    // manual credit adjustment = a transaction write
    @PostMapping("/accounts/{ownerType}/{ownerId}/adjust")
    @PreAuthorize("@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','WRITE')")
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

    // PD-40-02: PAYG is conceptually an account setting, but CREDIT_ACCOUNTS supports
    // READ only, so CREDIT_ACCOUNTS:UPDATE would be an un-allow-listed pair that can
    // never be granted. CREDIT_ACCOUNT_TRANSACTIONS:WRITE is the only writable
    // credit-domain verb and already gates adjust, the sibling money-moving handler;
    // reusing it here avoids a Resource.java enum change (a Phase-35 catalog change
    // with catalog-service, catalog-test and frontend implications).
    @PutMapping("/accounts/{ownerType}/{ownerId}/payg")
    @PreAuthorize("@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','WRITE')")
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
