package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.payments.dto.BalanceCreditResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.dto.PagedTransactionResponse;
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@ConditionalOnProperty(
        name = "app.enable.credit-service",
        havingValue = "true"
)
@Service
@Slf4j
public class CreditServiceImpl implements CreditService {
    private final RestClient creditServiceRestClient;

    public CreditServiceImpl(@Qualifier("creditServiceRestClient") RestClient creditServiceRestClient) {
        this.creditServiceRestClient = creditServiceRestClient;
    }

    @Override
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

    @Override
    public ResponseEntity<String> purchase(String requestBody, @Nullable String idempotencyKey) {
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

    @Override
    public BalanceCreditResponse getBalance(OwnerType ownerType, UUID ownerId) {
        return creditServiceRestClient.get()
                .uri("/credits/balance?ownerType={ownerType}&ownerId={ownerId}", ownerType, ownerId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(BalanceCreditResponse.class);
    }

    @Override
    public PagedTransactionResponse getTransactions(OwnerType ownerType, UUID ownerId, int page, int size) {
        return creditServiceRestClient.get()
                .uri("/credits/transactions?ownerType={ownerType}&ownerId={ownerId}&page={page}&size={size}",
                        ownerType, ownerId, page, size)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(PagedTransactionResponse.class);
    }
}
