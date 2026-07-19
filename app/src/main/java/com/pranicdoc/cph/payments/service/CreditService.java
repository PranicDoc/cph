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
    BalanceCreditResponse getBalance(OwnerType ownerType, UUID ownerId);
    PagedTransactionResponse getTransactions(OwnerType ownerType, UUID ownerId, int page, int size);
    AccountResponse createAccount(OwnerType ownerType, UUID ownerId);
    AccountResponse createAccount();
}
