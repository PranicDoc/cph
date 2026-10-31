package com.pranicdoc.cph.payments.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Slf4j
public class CreditServiceImpl implements CreditService {
    private final SecurityContextUtil securityContextUtil;
    private final CreditServiceClient creditServiceRestClient;
    private final HealerRepository healerRepository;
    private final CreditGateService creditGateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    // Same wholesale eviction as purchase(). A createOrder that is not a free-package purchase
    // changes no balance, so evicting there is a harmless refresh; the exhausted flag is only
    // cleared when the order came back PAID.
    @Caching(
        evict = {
            @CacheEvict(value = "dashboard.summary", key = "@securityContextUtil.getCurrentHealerId()"),
            @CacheEvict(value = "dashboard.credit-usage", allEntries = true)
        }
    )
    public String createOrder(String requestBody, String idempotencyKey) {
        assertCallerOwnsAccount(requestBody);
        String result = creditServiceRestClient.createOrder(requestBody, idempotencyKey);
        if ("PAID".equals(upstreamStatus(result))) {
            creditGateService.clearExhausted(securityContextUtil.getCurrentHealerId());
        }
        return result;
    }

    @Override
    @Caching(
        evict = {
            @CacheEvict(value = "dashboard.summary", key = "@securityContextUtil.getCurrentHealerId()"),
            @CacheEvict(value = "dashboard.credit-usage", allEntries = true)
        }
    )
    public String confirmOrder(String orderId, String requestBody) {
        assertCallerOwnsAccount(requestBody);
        String result = creditServiceRestClient.confirmOrder(orderId, requestBody);
        creditGateService.clearExhausted(securityContextUtil.getCurrentHealerId());
        return result;
    }

    /**
     * The account being bought for must be the caller's. HEALER: ownerId must be the caller's
     * healer id. CLINIC: cph has no clinic-membership check (the clinic tier was removed), so
     * this stays at parity with {@link #purchase} and applies no extra restriction.
     */
    private void assertCallerOwnsAccount(String requestBody) {
        JsonNode body;
        try {
            body = objectMapper.readTree(requestBody);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is not valid JSON");
        }
        if (body == null || !body.hasNonNull("ownerType") || !body.hasNonNull("ownerId")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ownerType and ownerId are required");
        }
        if ("HEALER".equals(body.get("ownerType").asText())) {
            UUID caller = securityContextUtil.getCurrentHealerId();
            if (caller == null || !caller.toString().equalsIgnoreCase(body.get("ownerId").asText())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your credit account");
            }
        }
    }

    private String upstreamStatus(String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            return n != null && n.hasNonNull("status") ? n.get("status").asText() : null;
        } catch (Exception e) {
            return null;
        }
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
        AccountResponse account = createAccount(OwnerType.HEALER, ownerId);
        // Credits can come back without passing through cph (a free-credit grant, a fix made in
        // creditService), which would leave the out-of-credits flag refusing Start for up to a
        // day. The UI reads this balance before offering Start, so a stale flag is dropped here.
        if (account != null && account.freeBalance() + account.paidBalance() > 0) {
            creditGateService.clearExhausted(ownerId);
        }
        return account;
    }

    @Override
    public PagedTransactionResponse getMyTransactions(int page, int size) {
        return getTransactions(OwnerType.HEALER, securityContextUtil.getCurrentHealerId(), page, size);
    }
}
