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
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditServiceClient {
    private final RestClient restClient;

    public CreditServiceClient(@Qualifier("creditServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public DeductResponse deduct(UUID healerId, String idempotencyKey) {
        return restClient.post()
                .uri("/credits/deduct")
                .header("X-Idempotency-Key", idempotencyKey)
                .body(new DeductRequest(healerId))
                .retrieve()
                .body(DeductResponse.class);
    }

    /** A global config value set by the super admin, if the key exists. */
    public Optional<String> configValue(String key) {
        CreditConfigEntry[] entries = restClient.get()
                .uri("/credits/admin/config")
                .retrieve()
                .body(CreditConfigEntry[].class);
        return entries == null ? Optional.empty() : Arrays.stream(entries)
                .filter(entry -> key.equals(entry.configKey()))
                .map(CreditConfigEntry::configValue)
                .findFirst();
    }

    public AccountResponse createAccount(OwnerType ownerType, UUID ownerId) {
        return restClient.post()
                .uri("/credits/accounts")
                .body(new CreateAccountRequest(ownerType.name(), ownerId))
                .retrieve()
                .body(AccountResponse.class);
    }

    /** Packages this account may buy now: while a paid plan is active only higher plans come back. */
    public String getPackages(OwnerType ownerType, UUID ownerId) {
        return restClient.get()
                .uri("/credits/packages?ownerType={ownerType}&ownerId={ownerId}", ownerType, ownerId)
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
