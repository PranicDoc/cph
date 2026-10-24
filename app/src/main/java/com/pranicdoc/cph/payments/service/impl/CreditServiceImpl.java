package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.dto.ApiResponse;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.AccountResponse;
import com.pranicdoc.cph.payments.dto.BalanceCreditResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.dto.PagedTransactionResponse;
import com.pranicdoc.cph.payments.service.CreditGateService;
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class CreditServiceImpl implements CreditService {
    private final SecurityContextUtil securityContextUtil;
    private final CreditServiceClient creditServiceRestClient;
    private final HealerRepository healerRepository;
    private final CreditGateService creditGateService;

    public CreditServiceImpl(SecurityContextUtil securityContextUtil, CreditServiceClient creditServiceRestClient,
                              HealerRepository healerRepository, CreditGateService creditGateService) {
        this.securityContextUtil = securityContextUtil;
        this.creditServiceRestClient = creditServiceRestClient;
        this.healerRepository = healerRepository;
        this.creditGateService = creditGateService;
    }

    @Override
    public ApiResponse<String> getPackages() {
        return ApiResponse.of(creditServiceRestClient.getPackages(
                OwnerType.HEALER, securityContextUtil.getCurrentHealerId()));
    }

    @Override
    // purchase() only receives the raw request body + idempotency key (no healerId/filter
    // params), so the cache keys can't be reconstructed from method args like the read-side
    // dashboard caches do -- resolve healerId from the security context instead, and evict
    // credit-usage wholesale since its key also composes a date-range filter we don't have here.
    @Caching(
        evict = {
            @CacheEvict(value = "dashboard.summary", key = "@securityContextUtil.getCurrentHealerId()"),
            @CacheEvict(value = "dashboard.credit-usage", allEntries = true)
        }
    )
    public String purchase(String requestBody, @Nullable String idempotencyKey) {
        String result = creditServiceRestClient.purchase(requestBody, idempotencyKey);
        creditGateService.clearExhausted(securityContextUtil.getCurrentHealerId());
        return result;
    }

    @Override
    public BalanceCreditResponse getBalance(OwnerType ownerType, UUID ownerId) {
        return creditServiceRestClient.getBalance(ownerType, ownerId);
    }

    @Override
    public PagedTransactionResponse getTransactions(OwnerType ownerType, UUID ownerId, int page, int size) {
        return creditServiceRestClient.getTransactions(ownerType, ownerId, page, size);
    }

    @Override
    public AccountResponse createAccount(OwnerType ownerType, UUID ownerId) {
        return creditServiceRestClient.createAccount(ownerType, ownerId);
    }

    @Override
    public AccountResponse createAccount() {
        UUID ownerId = securityContextUtil.getCurrentHealerId();
        return createAccount(OwnerType.HEALER, ownerId);
    }
}
