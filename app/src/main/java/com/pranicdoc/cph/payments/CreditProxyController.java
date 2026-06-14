package com.pranicdoc.cph.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

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
     * WR-02: upstream 4xx/5xx are forwarded with their original status code and body.
     */
    @GetMapping("/packages")
    public ResponseEntity<String> getPackages() {
        return creditServiceRestClient.get()
                .uri("/credits/packages")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .toEntity(String.class);
    }

    /**
     * POST /api/credits/purchase — forwards a purchase request to creditService.
     * Accepts and forwards the request body verbatim to avoid coupling cph to creditService DTO types.
     * X-Api-Key is added server-side.
     * CR-04: X-Idempotency-Key header is forwarded through to creditService (belt-and-suspenders).
     * WR-02: upstream 4xx/5xx are forwarded with their original status code and body.
     */
    @PostMapping("/purchase")
    public ResponseEntity<String> purchase(
            @RequestBody String requestBody,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        var spec = creditServiceRestClient.post()
                .uri("/credits/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody);
        if (idempotencyKey != null) {
            spec = spec.header("X-Idempotency-Key", idempotencyKey);
        }
        return spec.retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .toEntity(String.class);
    }
}
