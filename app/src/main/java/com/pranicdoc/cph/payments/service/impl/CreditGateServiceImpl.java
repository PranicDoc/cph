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
    public void checkAndDeduct(UUID healerId, UUID caseHealerId, UUID healingCaseId) {
        // caseHealerId is the case owner's real healerId (sourced from
        // HealingCase.getHealerId() at the CaseService call site) and is used only
        // to build the idempotency key below; it is not sent on the wire.
        String idempotencyKey = caseHealerId + "-" + healingCaseId;
        DeductResponse response = creditServiceClient.deduct(healerId, idempotencyKey);
        if ("INSUFFICIENT".equals(response.result())) {
            throw new InsufficientCreditsException();
        }
    }

    // TODO: switch to fail-closed + add PDF rate limiting per healer (Phase 5 hardening)
    private void checkAndDeductFallback(UUID healerId, UUID caseHealerId, UUID healingCaseId, Throwable t) {
        if (t instanceof InsufficientCreditsException ice) {
            throw ice;
        }
        log.warn("CREDIT_GATE_FAIL_OPEN caseId={} caseHealerId={} needsReconciliation=true reason={}",
                healingCaseId, caseHealerId, t.toString());
    }
}
