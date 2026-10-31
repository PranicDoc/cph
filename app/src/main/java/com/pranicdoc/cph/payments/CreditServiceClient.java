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

    public DeductResponse deduct(UUID healerId, String idempotencyKey) {
        return restClient.post()
                .uri("/credits/deduct")
                .header("X-Idempotency-Key", idempotencyKey)
                .body(new DeductRequest(healerId))
                .retrieve()
                .body(DeductResponse.class);
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

    /** Starts a Razorpay checkout order (or buys a free package outright); body is forwarded verbatim. */
    public String createOrder(String requestBody, String idempotencyKey) {
        return restClient.post()
                .uri("/credits/purchase/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", idempotencyKey)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(String.class);
    }

    /** Verifies the checkout payment signature and credits the purchase. */
    public String confirmOrder(String orderId, String requestBody) {
        return restClient.post()
                .uri("/credits/purchase/orders/{orderId}/confirm", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(String.class);
    }

    /** Passes a Razorpay webhook through byte-for-byte; the signature is over the exact raw body. */
    public void forwardRazorpayWebhook(byte[] rawBody, String signature) {
        restClient.post()
                .uri("/credits/webhooks/razorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", signature)
                .body(rawBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new ResponseStatusException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .toBodilessEntity();
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
