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
import com.pranicdoc.cph.payments.service.CreditService;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class CreditServiceImpl implements CreditService {
    private final SecurityContextUtil securityContextUtil;
    private final CreditServiceClient creditServiceRestClient;
    private final HealerRepository healerRepository;

    public CreditServiceImpl(SecurityContextUtil securityContextUtil, CreditServiceClient creditServiceRestClient,
                              HealerRepository healerRepository) {
        this.securityContextUtil = securityContextUtil;
        this.creditServiceRestClient = creditServiceRestClient;
        this.healerRepository = healerRepository;
    }

    @Override
    public ApiResponse<String> getPackages() {
        return ApiResponse.of(creditServiceRestClient.getPackages());
    }

    @Override
    public String purchase(String requestBody, @Nullable String idempotencyKey) {
        return creditServiceRestClient.purchase(requestBody, idempotencyKey);
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
    public AccountResponse createAccount(OwnerType ownerType) {
        UUID ownerId;
        if (OwnerType.HEALER.equals(ownerType)) {
            ownerId = securityContextUtil.getCurrentHealerId();
        } else {
            // CROSS-SERVICE BOUNDARY (Phase 27): creditService still keys CLINIC-owned accounts
            // on a clinic-shaped ownerId; sourced from the current healer's clinicId bridge
            // field until Phase 27 migrates accounts to OwnerType.HEALER. Do NOT remove.
            UUID currentHealerId = securityContextUtil.getCurrentHealerId();
            ownerId = healerRepository.findById(currentHealerId)
                    .map(Healer::getClinicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Healer", currentHealerId));
        }
        return createAccount(ownerType, ownerId);
    }
}
