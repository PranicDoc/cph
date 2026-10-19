package com.pranicdoc.cph.payments.service.impl;

import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.OwnerType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditServiceImplPackagesTest {

    @Test
    void packagesAreRequestedForTheCallersOwnAccountSoOnlyUpgradesComeBack() {
        UUID healerId = UUID.randomUUID();
        SecurityContextUtil securityContextUtil = mock(SecurityContextUtil.class);
        CreditServiceClient client = mock(CreditServiceClient.class);
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(client.getPackages(OwnerType.HEALER, healerId)).thenReturn("[{\"name\":\"ENTERPRISE\"}]");

        var service = new CreditServiceImpl(securityContextUtil, client, mock(HealerRepository.class));

        assertEquals("[{\"name\":\"ENTERPRISE\"}]", service.getPackages().data());
    }
}
