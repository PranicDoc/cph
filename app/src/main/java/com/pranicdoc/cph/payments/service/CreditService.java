package com.pranicdoc.cph.payments.service;

import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.payments.dto.AccountResponse;
import com.pranicdoc.cph.payments.dto.BalanceCreditResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.dto.PagedTransactionResponse;
import jakarta.annotation.Nullable;

import java.util.UUID;

public interface CreditService {
    ApiResponse<String> getPackages();
    String purchase(String requestBody, @Nullable String idempotencyKey);
    /** Starts a checkout order; the body's owner must be the caller. Returns the upstream JSON verbatim. */
    String createOrder(String requestBody, String idempotencyKey);
    /** Confirms a checkout payment; the body's owner must be the caller. Returns the upstream JSON verbatim. */
    String confirmOrder(String orderId, String requestBody);
    BalanceCreditResponse getBalance(OwnerType ownerType, UUID ownerId);
    PagedTransactionResponse getTransactions(OwnerType ownerType, UUID ownerId, int page, int size);
    AccountResponse createAccount(OwnerType ownerType, UUID ownerId);
    AccountResponse createAccount();

    /** The signed-in healer's own credit transactions. */
    PagedTransactionResponse getMyTransactions(int page, int size);
}
