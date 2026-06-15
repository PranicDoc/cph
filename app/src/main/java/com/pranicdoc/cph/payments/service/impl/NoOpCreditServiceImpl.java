package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.payments.dto.BalanceCreditResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.dto.PagedTransactionResponse;
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@ConditionalOnProperty(
        name = "app.enable.credit-service",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpCreditServiceImpl implements CreditService {

    @Override
    public ResponseEntity<String> getPackages() {
        log.debug("Using NOOP credit service — getPackages() returning empty list");
        return ResponseEntity.ok("[]");
    }

    @Override
    public ResponseEntity<String> purchase(String requestBody, @Nullable String idempotencyKey) {
        log.debug("Using NOOP credit service — purchase() is a no-op");
        return ResponseEntity.ok("{}");
    }

    @Override
    public BalanceCreditResponse getBalance(OwnerType ownerType, UUID ownerId) {
        log.debug("Using NOOP credit service — getBalance() returning null");
        return null;
    }

    @Override
    public PagedTransactionResponse getTransactions(OwnerType ownerType, UUID ownerId, int page, int size) {
        log.debug("Using NOOP credit service — getTransactions() returning empty page");
        return new PagedTransactionResponse(List.of(), 0, 0L, 0, 0, true);
    }
}
