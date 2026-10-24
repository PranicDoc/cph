package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.common.exception.InsufficientCreditsException;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.DeductResponse;
import com.pranicdoc.cph.payments.service.CreditGateService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditGateServiceImpl implements CreditGateService {

    private static final Logger log = LoggerFactory.getLogger(CreditGateService.class);

    // Set when creditService says a healer is out of credits, so new charges can be refused without
    // calling it (see isExhausted). Every top-up passes through cph and clears the flag; the TTL
    // only covers a missed clear.
    private static final String EXHAUSTED_KEY = "credits:exhausted:";
    private static final Duration EXHAUSTED_TTL = Duration.ofHours(24);

    private final CreditServiceClient creditServiceClient;
    // Optional: without Redis (tests, outage) every check simply goes to creditService.
    private final ObjectProvider<StringRedisTemplate> redis;

    @Override
    @CircuitBreaker(name = "creditService", fallbackMethod = "checkAndDeductFallback")
    public void checkAndDeduct(UUID healerId, UUID caseHealerId, UUID healingCaseId) {
        // caseHealerId is the case owner's real healerId (sourced from
        // HealingCase.getHealerId() at the CaseService call site) and is used only
        // to build the idempotency key below; it is not sent on the wire.
        String idempotencyKey = caseHealerId + "-" + healingCaseId;
        DeductResponse response = creditServiceClient.deduct(healerId, idempotencyKey);
        if ("INSUFFICIENT".equals(response.result())) {
            markExhausted(healerId);
            throw new InsufficientCreditsException();
        }
    }

    @Override
    public void clearExhausted(UUID healerId) {
        try {
            StringRedisTemplate template = redis.getIfAvailable();
            if (template != null) template.delete(EXHAUSTED_KEY + healerId);
        } catch (RuntimeException e) {
            log.warn("Could not clear the out-of-credits flag for healer {}: {}", healerId, e.toString());
        }
    }

    @Override
    public boolean isExhausted(UUID healerId) {
        try {
            StringRedisTemplate template = redis.getIfAvailable();
            return template != null && Boolean.TRUE.equals(template.hasKey(EXHAUSTED_KEY + healerId));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void markExhausted(UUID healerId) {
        try {
            StringRedisTemplate template = redis.getIfAvailable();
            if (template != null) template.opsForValue().set(EXHAUSTED_KEY + healerId, "1", EXHAUSTED_TTL);
        } catch (RuntimeException e) {
            log.warn("Could not set the out-of-credits flag for healer {}: {}", healerId, e.toString());
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
