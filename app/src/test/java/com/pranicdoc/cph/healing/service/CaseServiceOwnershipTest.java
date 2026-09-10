package com.pranicdoc.cph.healing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.dto.CaseResponse;
import com.pranicdoc.cph.healing.dto.SymptomFeedbackDTO;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.healing.service.impl.CaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Negative-case denial proofs for 42-15 (CR-01 + CR-02) — a passing test here fails if
 * either IDOR is ever reintroduced. Deliberately NOT added to {@link CaseServiceTest};
 * that class is one of the six 42-BASELINE OUT classes and cannot be asserted green.
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceOwnershipTest {

    @Mock private CaseRepository caseRepository;
    @Mock private SecurityContextUtil securityContextUtil;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private CaseService caseService;

    /**
     * Builds a minimal, persistable-looking {@link HealingCase} with the given patient/healer
     * ownership. The Lombok builder does not include {@code BaseEntity} fields, so the row id
     * is assigned via the inherited setter after {@code build()} (same note as
     * {@code CaseServiceTest:144}). {@code labFindings} is left null so {@code toResponse}
     * short-circuits before touching {@code objectMapper}.
     */
    private HealingCase caseFixture(UUID id, UUID patientId, UUID healerId) {
        HealingCase entity = HealingCase.builder()
                .patientId(patientId)
                .build();
        entity.setId(id);
        entity.setHealerId(healerId);
        return entity;
    }

    // ─── CR-01: getByIdForSelfService ────────────────────────────────────

    @Test
    @DisplayName("CR-01: a PATIENT token bound to case A is denied when it changes the caseId "
            + "query parameter to request case B")
    void getByIdForSelfService_patientTokenBoundToAnotherCase_isDenied() {
        UUID caseAId = UUID.randomUUID();
        UUID caseBId = UUID.randomUUID();
        UUID patientA = UUID.randomUUID();
        UUID patientB = UUID.randomUUID();
        HealingCase caseB = caseFixture(caseBId, patientB, UUID.randomUUID());

        when(caseRepository.findById(caseBId)).thenReturn(Optional.of(caseB));
        when(securityContextUtil.getCurrentRole()).thenReturn("PATIENT");
        when(securityContextUtil.getCurrentCaseId()).thenReturn(caseAId);
        when(securityContextUtil.getCurrentPatientId()).thenReturn(patientA);

        assertThatThrownBy(() -> caseService.getByIdForSelfService(caseBId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CR-01 non-vacuity: the same patient requesting its OWN case succeeds")
    void getByIdForSelfService_patientTokenBoundToItsOwnCase_returnsTheCase() {
        UUID caseId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        HealingCase fixture = caseFixture(caseId, patientId, UUID.randomUUID());

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fixture));
        when(securityContextUtil.getCurrentRole()).thenReturn("PATIENT");
        when(securityContextUtil.getCurrentCaseId()).thenReturn(caseId);
        when(securityContextUtil.getCurrentPatientId()).thenReturn(patientId);

        CaseResponse response = caseService.getByIdForSelfService(caseId);

        assertThat(response.id()).isEqualTo(caseId);
    }

    @Test
    @DisplayName("CR-01 fail-closed: null caseId/patientId claims (pre-case SELF_REG shape) are denied")
    void getByIdForSelfService_tokenWithNullCaseAndPatientClaims_isDenied() {
        UUID caseId = UUID.randomUUID();
        HealingCase fixture = caseFixture(caseId, UUID.randomUUID(), UUID.randomUUID());

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fixture));
        when(securityContextUtil.getCurrentRole()).thenReturn("PATIENT");
        when(securityContextUtil.getCurrentCaseId()).thenReturn(null);
        when(securityContextUtil.getCurrentPatientId()).thenReturn(null);

        assertThatThrownBy(() -> caseService.getByIdForSelfService(caseId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CR-01 defence in depth: a HEALER-role caller is denied on this patient-only route")
    void getByIdForSelfService_healerRoleCaller_isDenied() {
        UUID caseId = UUID.randomUUID();
        HealingCase fixture = caseFixture(caseId, UUID.randomUUID(), UUID.randomUUID());

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fixture));
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");

        assertThatThrownBy(() -> caseService.getByIdForSelfService(caseId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── CR-02: getSymptomsForFeedback ───────────────────────────────────

    @Test
    @DisplayName("CR-02 cross-tenant: a HEALER token for another healer's case is denied and the "
            + "symptom query is never reached")
    void getSymptomsForFeedback_healerTokenForAnotherHealersCase_isDeniedAndNeverQueriesSymptoms() {
        UUID caseId = UUID.randomUUID();
        UUID healer1 = UUID.randomUUID();
        UUID healer2 = UUID.randomUUID();
        HealingCase fixture = caseFixture(caseId, UUID.randomUUID(), healer2);
        Pageable pageable = Pageable.unpaged();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fixture));
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healer1);

        assertThatThrownBy(() -> caseService.getSymptomsForFeedback(caseId, pageable))
                .isInstanceOf(AccessDeniedException.class);
        verify(caseRepository, never()).findAllSymptomsByCaseId(any(), any());
    }

    @Test
    @DisplayName("CR-02 cross-patient: a PATIENT token bound to another case is denied and the "
            + "symptom query is never reached")
    void getSymptomsForFeedback_patientTokenBoundToAnotherCase_isDeniedAndNeverQueriesSymptoms() {
        UUID caseAId = UUID.randomUUID();
        UUID caseBId = UUID.randomUUID();
        HealingCase caseB = caseFixture(caseBId, UUID.randomUUID(), UUID.randomUUID());
        Pageable pageable = Pageable.unpaged();

        when(caseRepository.findById(caseBId)).thenReturn(Optional.of(caseB));
        when(securityContextUtil.getCurrentRole()).thenReturn("PATIENT");
        when(securityContextUtil.getCurrentCaseId()).thenReturn(caseAId);

        assertThatThrownBy(() -> caseService.getSymptomsForFeedback(caseBId, pageable))
                .isInstanceOf(AccessDeniedException.class);
        verify(caseRepository, never()).findAllSymptomsByCaseId(any(), any());
    }

    @Test
    @DisplayName("CR-02 non-vacuity: the owning healer's request queries with the LOADED case id")
    void getSymptomsForFeedback_owningHealer_queriesWithTheLoadedCaseId() {
        UUID caseId = UUID.randomUUID();
        UUID healerId = UUID.randomUUID();
        HealingCase fixture = caseFixture(caseId, UUID.randomUUID(), healerId);
        Pageable pageable = Pageable.unpaged();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fixture));
        when(securityContextUtil.getCurrentRole()).thenReturn("HEALER");
        when(securityContextUtil.getCurrentHealerId()).thenReturn(healerId);
        when(caseRepository.findAllSymptomsByCaseId(eq(caseId), any()))
                .thenReturn(Page.<SymptomFeedbackDTO>empty(pageable));

        caseService.getSymptomsForFeedback(caseId, pageable);

        verify(caseRepository).findAllSymptomsByCaseId(eq(caseId), any());
    }

    @Test
    @DisplayName("CR-02 non-vacuity: a feedback-session token bound to this case is allowed even "
            + "without a patientId claim — proves the fix doesn't 403 the live feedback form")
    void getSymptomsForFeedback_feedbackTokenBoundToThatCase_isAllowedEvenWithoutAPatientClaim() {
        UUID caseId = UUID.randomUUID();
        HealingCase fixture = caseFixture(caseId, UUID.randomUUID(), UUID.randomUUID());
        Pageable pageable = Pageable.unpaged();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(fixture));
        when(securityContextUtil.getCurrentRole()).thenReturn("PATIENT");
        when(securityContextUtil.getCurrentCaseId()).thenReturn(caseId);
        when(securityContextUtil.getCurrentPatientId()).thenReturn(null);
        when(caseRepository.findAllSymptomsByCaseId(eq(caseId), any()))
                .thenReturn(Page.<SymptomFeedbackDTO>empty(pageable));

        caseService.getSymptomsForFeedback(caseId, pageable);

        verify(caseRepository).findAllSymptomsByCaseId(eq(caseId), any());
    }
}
