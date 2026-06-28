package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.payments.dto.*;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class CreditServiceClient {
    private final RestClient restClient;

    public CreditServiceClient(@Qualifier("creditServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public DeductResponse deduct(UUID healerId, UUID clinicId, String idempotencyKey) {
        return restClient.post()
                .uri("/credits/deduct")
                .header("X-Idempotency-Key", idempotencyKey)
                .body(new DeductRequest(healerId, clinicId))
                .retrieve()
                .body(DeductResponse.class);
    }

    public AccountResponse createAccount(OwnerType ownerType, UUID ownerId) {
        return restClient.post()
                .uri("/credits/accounts")
                .body(new CreateAccountRequest(ownerType.name(), ownerId, null))
                .retrieve()
                .body(AccountResponse.class);
    }

    public String getPackages() {
        return restClient.get()
                .uri("/credits/packages")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(String.class);
    }

    public String purchase(String requestBody, @Nullable String idempotencyKey) {
        var spec = restClient.post()
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
                .body(String.class);
    }

    public BalanceCreditResponse getBalance(OwnerType ownerType, UUID ownerId) {
        return restClient.get()
                .uri("/credits/balance?ownerType={ownerType}&ownerId={ownerId}", ownerType, ownerId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(BalanceCreditResponse.class);
    }

    public PagedTransactionResponse getTransactions(OwnerType ownerType, UUID ownerId, int page, int size) {
        return restClient.get()
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
