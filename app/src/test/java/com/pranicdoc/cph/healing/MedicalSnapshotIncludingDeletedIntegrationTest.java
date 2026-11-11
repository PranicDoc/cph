package com.pranicdoc.cph.healing;

import com.pranicdoc.cph.auth.service.HealerCacheService;
import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.common.tenant.TenantContext;
import com.pranicdoc.cph.healing.model.CaseMedicalSnapshot;
import com.pranicdoc.cph.healing.model.HealingCase;
import com.pranicdoc.cph.healing.repository.CaseMedicalSnapshotRepository;
import com.pranicdoc.cph.patient.dto.PatientCreateRequest;
import com.pranicdoc.cph.patient.model.Patient;
import com.pranicdoc.cph.patient.model.PatientMedicalHistory;
import com.pranicdoc.cph.patient.repository.PatientMedicalHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB check that the upsert lookups see soft-deleted rows. Both tables are 1:1 with a UNIQUE key
 * (uq_case_medical_snapshot_case, uq_patient_medical_history_pid) and hidden by @SQLRestriction once
 * is_deleted = true; if these lookups missed them, the upsert would insert a second row and fail.
 */
@DataJpaTest
@ActiveProfiles("test")
class MedicalSnapshotIncludingDeletedIntegrationTest {

    @Autowired private TestEntityManager em;
    @Autowired private CaseMedicalSnapshotRepository snapshotRepository;
    @Autowired private PatientMedicalHistoryRepository historyRepository;

    @MockBean private HealerCacheService healerCacheService;
    @MockBean private SecurityContextUtil securityContextUtil;

    private final UUID healerId = UUID.randomUUID();

    @BeforeEach
    void setTenant() {
        TenantContext.set(healerId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void findIncludingDeletedByHealingCaseId_returnsSoftDeletedSnapshot() {
        HealingCase healingCase = HealingCase.builder().patientId(UUID.randomUUID()).status(CaseStatus.ACTIVE).build();
        healingCase.setHealerId(healerId);
        em.persist(healingCase);
        CaseMedicalSnapshot snapshot = CaseMedicalSnapshot.builder().healingCase(healingCase).additionalNotes("n").build();
        em.persist(snapshot);
        snapshot.softDelete();
        em.flush();
        em.clear();

        assertThat(snapshotRepository.findById(snapshot.getId())).as("hidden by @SQLRestriction").isEmpty();
        assertThat(snapshotRepository.findIncludingDeletedByHealingCaseId(healingCase.getId()))
                .get().extracting(CaseMedicalSnapshot::getId).isEqualTo(snapshot.getId());
        assertThat(snapshotRepository.findIncludingDeletedByHealingCaseId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findIncludingDeletedByPatientId_returnsSoftDeletedHistory() {
        Patient patient = Patient.create(new PatientCreateRequest("Asha", "Rao", null, null, null, null,
                "+911111111111", null, null, null, "Pune", null, null, null), healerId);
        em.persist(patient);
        PatientMedicalHistory history = new PatientMedicalHistory();
        history.setPatient(patient);
        history.setHealerId(healerId);
        history.setAdditionalNotes("n");
        em.persist(history);
        history.softDelete();
        em.flush();
        em.clear();

        assertThat(historyRepository.findById(history.getId())).as("hidden by @SQLRestriction").isEmpty();
        assertThat(historyRepository.findIncludingDeletedByPatientId(patient.getId()))
                .get().extracting(PatientMedicalHistory::getId).isEqualTo(history.getId());
    }
}
