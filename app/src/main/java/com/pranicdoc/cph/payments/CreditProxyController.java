package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.payments.dto.AccountResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/**
 * Proxy controller that forwards credit-related requests to creditService.
 *
 * <p>The X-Api-Key is added server-side by the {@code creditServiceRestClient} RestClient bean
 * and is never exposed to the browser. This controller exposes three endpoints
 * ({@code GET /packages}, {@code POST /purchase}, {@code POST /account}). Authentication is
 * still enforced by {@code SecurityConfig}'s {@code .anyRequest().authenticated()}; as of
 * Phase 42 each endpoint additionally requires a specific {@code (resource, verb)} capability
 * evaluated by {@link com.pranicdoc.cph.rbac.RbacAuthz} — a SUPADM caller passes via the
 * single short-circuit, every other caller only if granted the named pair.</p>
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditProxyController {

    private final CreditService creditService;

    /**
     * GET /api/credits/packages — returns available credit packages from creditService.
     * Forwards to creditService GET /credits/packages with X-Api-Key added server-side.
     * WR-02: upstream 4xx/5xx are forwarded with their original status code and body.
     */
    @PreAuthorize("@rbacAuthz.hasPermission('CREDIT_ACCOUNTS','READ')")
    @GetMapping("/packages")
    public ResponseEntity<ApiResponse<String>> getPackages() {
        return ResponseEntity.ok(creditService.getPackages());
    }

    /**
     * POST /api/credits/purchase — forwards a purchase request to creditService.
     * Accepts and forwards the request body verbatim to avoid coupling cph to creditService DTO types.
     * X-Api-Key is added server-side.
     * CR-04: X-Idempotency-Key header is forwarded through to creditService (belt-and-suspenders).
     * WR-02: upstream 4xx/5xx are forwarded with their original status code and body.
     */
    // Verb choice mirrors Phase 40 PD-40-02: CREDIT_ACCOUNTS has no UPDATE/WRITE, so the
    // writable credit-domain verb is CREDIT_ACCOUNT_TRANSACTIONS:WRITE — the same pair
    // AdminCreditController.adjust uses.
    @PreAuthorize("@rbacAuthz.hasPermission('CREDIT_ACCOUNT_TRANSACTIONS','WRITE')")
    @PostMapping("/purchase")
    public ResponseEntity<String> purchase(
            @RequestBody String requestBody,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(creditService.purchase(requestBody, idempotencyKey));
    }

    // D-04: CREDIT_ACCOUNTS gained the CREATE verb this phase specifically because
    // POST /account (idempotent find-or-create of the caller's own credit account) had no
    // fitting existing verb; this @PreAuthorize is its only enforcement site.
    @PreAuthorize("@rbacAuthz.hasPermission('CREDIT_ACCOUNTS','CREATE')")
    @PostMapping("/account")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount() {
        return ResponseEntity.ok(ApiResponse.of(creditService.createAccount()));
    }
}
