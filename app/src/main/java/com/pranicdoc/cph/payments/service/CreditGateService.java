package com.pranicdoc.cph.payments.service;

import java.util.UUID;

public interface CreditGateService {
    void checkAndDeduct(UUID healerId, UUID caseHealerId, UUID healingCaseId);

    /**
     * True when the healer was last seen out of credits. Use it only before a NEW charge: a
     * repeated charge for something already paid is still approved by creditService.
     */
    boolean isExhausted(UUID healerId);

    /** Forget that the healer was out of credits; call after any top-up. */
    void clearExhausted(UUID healerId);
}
