package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.common.exception.InsufficientCreditsException;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.DeductResponse;
import com.pranicdoc.cph.payments.service.CreditGateService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditGateServiceImpl implements CreditGateService {

    private static final Logger log = LoggerFactory.getLogger(CreditGateService.class);

    private final CreditServiceClient creditServiceClient;

    @Override
    @CircuitBreaker(name = "creditService", fallbackMethod = "checkAndDeductFallback")
    public void checkAndDeduct(UUID healerId, UUID clinicId, UUID healingCaseId) {
        // CROSS-SERVICE BOUNDARY (Phase 27): clinicId here is the caller-sourced clinic-shaped
        // ownerId (26-07: sourced from healer.clinicId bridge, not the JWT clinicId claim).
        // Shape/idempotency key preserved unchanged so creditService's contract is untouched.
        // TODO(Phase 27): idempotencyKey + DeductRequest.clinicId retire when creditService
        // moves to OwnerType.HEALER.
        String idempotencyKey = clinicId + "-" + healingCaseId;
        DeductResponse response = creditServiceClient.deduct(healerId, clinicId, idempotencyKey);
        if ("INSUFFICIENT".equals(response.result())) {
            throw new InsufficientCreditsException();
        }
    }

    // TODO: switch to fail-closed + add PDF rate limiting per clinic (Phase 5 hardening)
    private void checkAndDeductFallback(UUID healerId, UUID clinicId, UUID healingCaseId, Throwable t) {
        if (t instanceof InsufficientCreditsException ice) {
            throw ice;
        }
        log.warn("CREDIT_GATE_FAIL_OPEN caseId={} clinicId={} needsReconciliation=true reason={}",
                healingCaseId, clinicId, t.toString());
    }
}
