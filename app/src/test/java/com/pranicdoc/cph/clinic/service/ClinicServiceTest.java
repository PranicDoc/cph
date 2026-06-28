package com.pranicdoc.cph.clinic.service;

import com.pranicdoc.cph.clinic.dto.ClinicCreateRequest;
import com.pranicdoc.cph.clinic.dto.ClinicResponse;
import com.pranicdoc.cph.clinic.model.Clinic;
import com.pranicdoc.cph.clinic.repository.ClinicRepository;
import com.pranicdoc.cph.payments.CreditServiceClient;
import com.pranicdoc.cph.payments.dto.AccountResponse;
import com.pranicdoc.cph.payments.dto.OwnerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicServiceTest {

    @Mock private ClinicRepository clinicRepository;
    @Mock private CreditServiceClient creditServiceClient;

    @InjectMocks
    private ClinicService clinicService;

    private ClinicCreateRequest sampleRequest() {
        return new ClinicCreateRequest("CLINIC01", "Test Clinic", "+91-9999999999",
                "test@clinic.com", "123 Main St", null, "Chennai", "TN", "600001");
    }

    private Clinic savedClinic(UUID id) {
        return Clinic.builder()
                .code("CLINIC01").name("Test Clinic").active(true).build();
    }

    @Test
    void create_provisionsCreditAccountAndReturnsResponse() {
        UUID clinicId = UUID.randomUUID();
        Clinic clinic = savedClinic(clinicId);
        AccountResponse accountResponse = new AccountResponse(UUID.randomUUID(), "CLINIC", clinicId, 3);

        when(clinicRepository.save(any())).thenReturn(clinic);
        when(creditServiceClient.createAccount(eq(OwnerType.CLINIC), any())).thenReturn(accountResponse);

        ClinicResponse response = clinicService.create(sampleRequest());

        assertNotNull(response);
        verify(creditServiceClient).createAccount(eq(OwnerType.CLINIC), any());
        verify(clinicRepository).save(any());
    }

    @Test
    void create_provisioningFails_clinicStillCreatedAndDoesNotThrow() {
        Clinic clinic = savedClinic(UUID.randomUUID());

        when(clinicRepository.save(any())).thenReturn(clinic);
        when(creditServiceClient.createAccount(any(), any()))
                .thenThrow(new RuntimeException("creditService unavailable"));

        assertDoesNotThrow(() -> clinicService.create(sampleRequest()));
        verify(clinicRepository).save(any());
    }
}
