package com.pranicdoc.cph.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Proxy controller that forwards credit-related requests to creditService.
 *
 * <p>The X-Api-Key is added server-side by the {@code creditServiceRestClient} RestClient bean
 * and is never exposed to the browser. Both endpoints are protected by cph's existing JWT
 * SecurityFilterChain (any authenticated request to /api/** is allowed).</p>
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditProxyController {

    @Qualifier("creditServiceRestClient")
    private final RestClient creditServiceRestClient;

    /**
     * GET /api/credits/packages — returns available credit packages from creditService.
     * Forwards to creditService GET /credits/packages with X-Api-Key added server-side.
     */
    @GetMapping("/packages")
    public ResponseEntity<String> getPackages() {
        return creditServiceRestClient.get()
                .uri("/credits/packages")
                .retrieve()
                .toEntity(String.class);
    }

    /**
     * POST /api/credits/purchase — forwards a purchase request to creditService.
     * Accepts and forwards the request body verbatim to avoid coupling cph to creditService DTO types.
     * X-Api-Key is added server-side.
     */
    @PostMapping("/purchase")
    public ResponseEntity<String> purchase(@RequestBody String requestBody) {
        return creditServiceRestClient.post()
                .uri("/credits/purchase")
                .body(requestBody)
                .retrieve()
                .toEntity(String.class);
    }
}
