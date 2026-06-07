package com.pranicdoc.cph.payments;

import com.pranicdoc.cph.common.exception.InsufficientCreditsException;
import com.pranicdoc.cph.payments.dto.DeductResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditGateService {

    private static final Logger log = LoggerFactory.getLogger(CreditGateService.class);

    private final CreditServiceClient creditServiceClient;

    @CircuitBreaker(name = "creditService", fallbackMethod = "checkAndDeductFallback")
    public void checkAndDeduct(UUID healerId, UUID clinicId, UUID healingCaseId) {
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
