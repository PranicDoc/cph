package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.payments.dto.AccountResponse;
import com.pranicdoc.cph.payments.dto.CreateAccountRequest;
import com.pranicdoc.cph.payments.dto.DeductRequest;
import com.pranicdoc.cph.payments.dto.DeductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    public AccountResponse createAccount(String ownerType, UUID ownerId) {
        return restClient.post()
                .uri("/credits/accounts")
                .body(new CreateAccountRequest(ownerType, ownerId, null))
                .retrieve()
                .body(AccountResponse.class);
    }
}
