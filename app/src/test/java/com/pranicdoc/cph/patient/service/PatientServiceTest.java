package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.healing.mapper.DeclarationMapper;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.patient.dto.PatientCaseResponse;
import com.pranicdoc.cph.patient.dto.PatientSort;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.patient.repository.RegistrationLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private RegistrationLinkRepository registrationLinkRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private DeclarationMapper declarationMapper;
    @Mock private SecurityContextUtil securityContextUtil;

    @InjectMocks private PatientService patientService;

    private static final Instant MAX_CASE_DATE = Instant.parse("9999-12-31T00:00:00Z");

    private UUID clinicId;

    @BeforeEach
    void setUp() {
        clinicId = UUID.randomUUID();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllPatients_noFilters_queriesAllStatusesOpenDateRangeAndDefaultSort() {
        when(securityContextUtil.getCurrentClinicId()).thenReturn(clinicId);
        List<PatientCaseResponse> expected = List.of(mock(PatientCaseResponse.class));
        when(patientRepository.fetchPatientsWithLatestCaseStatus(
                eq(clinicId), any(Collection.class), any(Instant.class), any(Instant.class), any(Sort.class)))
                .thenReturn(expected);

        List<PatientCaseResponse> result = patientService.getAllPatients(null, null, null, null);

        assertEquals(expected, result);
        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                clinicId, List.of(CaseStatus.values()), Instant.EPOCH, MAX_CASE_DATE,
                PatientSort.CASE_DATE_DESC.toSort());
    }

    @Test
    void getAllPatients_statusFilter_passesSingleStatus() {
        when(securityContextUtil.getCurrentClinicId()).thenReturn(clinicId);

        patientService.getAllPatients(CaseStatus.ACTIVE, null, null, null);

        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                clinicId, List.of(CaseStatus.ACTIVE), Instant.EPOCH, MAX_CASE_DATE,
                PatientSort.CASE_DATE_DESC.toSort());
    }

    @Test
    void getAllPatients_dateRange_convertsToUtcBoundsWithExclusiveEnd() {
        when(securityContextUtil.getCurrentClinicId()).thenReturn(clinicId);

        patientService.getAllPatients(
                null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), null);

        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                clinicId,
                List.of(CaseStatus.values()),
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-06T00:00:00Z"),
                PatientSort.CASE_DATE_DESC.toSort());
    }

    @Test
    void getAllPatients_explicitSort_passesMappedSort() {
        when(securityContextUtil.getCurrentClinicId()).thenReturn(clinicId);

        patientService.getAllPatients(null, null, null, PatientSort.NAME_ASC);

        verify(patientRepository).fetchPatientsWithLatestCaseStatus(
                clinicId, List.of(CaseStatus.values()), Instant.EPOCH, MAX_CASE_DATE,
                PatientSort.NAME_ASC.toSort());
    }

    @Test
    void getAllPatients_endDateBeforeStartDate_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                patientService.getAllPatients(
                        null, LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 1), null));
        verifyNoInteractions(patientRepository);
    }
}
