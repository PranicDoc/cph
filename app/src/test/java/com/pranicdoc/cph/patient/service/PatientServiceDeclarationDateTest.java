package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.exception.CaseLockedException;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.model.Declaration;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.patient.dto.DeclarationDateRequest;
import com.pranicdoc.cph.patient.dto.DeclarationDateResponse;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientServiceDeclarationDateTest {

    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID HEALER_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 15);

    @Mock private PatientRepository patientRepository;
    @Mock private RegistrationLinkRepository registrationLinkRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private DeclarationMapper declarationMapper;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private PatientTrashService trashService;

    @InjectMocks private PatientService patientService;

    @BeforeEach
    void ownPatient() {
        Patient patient = mock(Patient.class);
        when(patient.getHealerId()).thenReturn(HEALER_ID);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(securityContextUtil.getCurrentRole()).thenReturn("PRACTITIONER");
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(HEALER_ID);
    }

    private HealingCase latestCase(CaseStatus status, Declaration declaration) {
        HealingCase healingCase = HealingCase.builder().build();
        healingCase.setStatus(status);
        healingCase.setDeclaration(declaration);
        when(caseRepository.findFirstByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(Optional.of(healingCase));
        return healingCase;
    }

    private static Declaration signedOn(Instant signedAt) {
        return Declaration.builder().signedAt(signedAt).build();
    }

    @Test
    void readsTheDateOfTheLatestCaseInIndiaTime() {
        latestCase(CaseStatus.ACTIVE, signedOn(Instant.parse("2026-08-14T19:00:00Z")));

        assertThat(patientService.getDeclarationDate(PATIENT_ID))
                .isEqualTo(new DeclarationDateResponse(DATE, true, true));
    }

    @Test
    void aLockedCaseIsReadOnly() {
        latestCase(CaseStatus.LOCKED, signedOn(Instant.parse("2026-08-15T06:30:00Z")));

        assertThat(patientService.getDeclarationDate(PATIENT_ID))
                .isEqualTo(new DeclarationDateResponse(DATE, true, false));
    }

    @Test
    void noCaseOrNoDeclarationIsReportedWithoutAnError() {
        when(caseRepository.findFirstByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(Optional.empty());
        assertThat(patientService.getDeclarationDate(PATIENT_ID))
                .isEqualTo(new DeclarationDateResponse(null, false, false));

        latestCase(CaseStatus.ACTIVE, null);
        assertThat(patientService.getDeclarationDate(PATIENT_ID))
                .isEqualTo(new DeclarationDateResponse(null, false, false));
    }

    @Test
    void updateStoresNoonIndiaTimeOnTheLatestCase() {
        Declaration declaration = signedOn(Instant.parse("2026-11-04T10:00:00Z"));
        HealingCase healingCase = latestCase(CaseStatus.CLOSED, declaration);

        DeclarationDateResponse response =
                patientService.updateDeclarationDate(PATIENT_ID, new DeclarationDateRequest(DATE));

        assertThat(declaration.getSignedAt()).isEqualTo(Instant.parse("2026-08-15T06:30:00Z"));
        assertThat(response).isEqualTo(new DeclarationDateResponse(DATE, true, true));
        verify(caseRepository).save(healingCase);
    }

    @Test
    void updateIsRefusedWhileTheCaseIsLocked() {
        Declaration declaration = signedOn(Instant.parse("2026-11-04T10:00:00Z"));
        latestCase(CaseStatus.LOCKED, declaration);

        assertThatThrownBy(() -> patientService.updateDeclarationDate(PATIENT_ID, new DeclarationDateRequest(DATE)))
                .isInstanceOf(CaseLockedException.class);

        assertThat(declaration.getSignedAt()).isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
        verify(caseRepository, never()).save(any());
    }

    @Test
    void updateRefusesAFutureDateAndChangesNothing() {
        Declaration declaration = signedOn(Instant.parse("2026-11-04T10:00:00Z"));
        latestCase(CaseStatus.ACTIVE, declaration);
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);

        assertThatThrownBy(() -> patientService.updateDeclarationDate(PATIENT_ID, new DeclarationDateRequest(tomorrow)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(declaration.getSignedAt()).isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
        verify(caseRepository, never()).save(any());
    }

    @Test
    void updateWithNoCaseOrDeclarationIsNotFound() {
        when(caseRepository.findFirstByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> patientService.updateDeclarationDate(PATIENT_ID, new DeclarationDateRequest(DATE)))
                .isInstanceOf(ResourceNotFoundException.class);

        latestCase(CaseStatus.ACTIVE, null);
        assertThatThrownBy(() -> patientService.updateDeclarationDate(PATIENT_ID, new DeclarationDateRequest(DATE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anotherHealersPatientIsNotFoundForReadAndWrite() {
        when(securityContextUtil.getCurrentHealerIdOrNull()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> patientService.getDeclarationDate(PATIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> patientService.updateDeclarationDate(PATIENT_ID, new DeclarationDateRequest(DATE)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(caseRepository, never()).findFirstByPatientIdOrderByCreatedAtDesc(any());
    }
}
