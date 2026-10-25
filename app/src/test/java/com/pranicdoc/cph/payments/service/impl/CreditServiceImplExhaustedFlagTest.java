package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.AccountResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import com.pranicdoc.cph.payments.service.CreditGateService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reading the balance drops an out-of-credits flag that no longer matches it. */
class CreditServiceImplExhaustedFlagTest {

    private final UUID healerId = UUID.randomUUID();
    private final SecurityContextUtil securityContextUtil = mock(SecurityContextUtil.class);
    private final CreditServiceClient client = mock(CreditServiceClient.class);
    private final CreditGateService creditGateService = mock(CreditGateService.class);
    private final CreditServiceImpl service =
            new CreditServiceImpl(securityContextUtil, client, mock(HealerRepository.class), creditGateService);

    private void balance(int free, int paid) {
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(client.createAccount(OwnerType.HEALER, healerId)).thenReturn(
                new AccountResponse(UUID.randomUUID(), "HEALER", healerId, free, paid, "FREE", null));
    }

    @Test
    void creditsLeft_clearTheFlagSoStartWorksAgain() {
        balance(3, 0);

        service.createAccount();

        verify(creditGateService).clearExhausted(healerId);
    }

    @Test
    void noCreditsLeft_keepTheFlag() {
        balance(0, 0);

        service.createAccount();

        verify(creditGateService, never()).clearExhausted(healerId);
    }
}
