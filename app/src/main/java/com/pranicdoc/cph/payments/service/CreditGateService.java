package com.pranicdoc.cph.payments.service;

import java.util.UUID;

public interface CreditGateService {
    void checkAndDeduct(UUID healerId, UUID caseHealerId, UUID healingCaseId);
}
