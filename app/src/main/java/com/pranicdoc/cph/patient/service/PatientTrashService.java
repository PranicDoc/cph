package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.healing.repository.CaseRepository;
import com.pranicdoc.cph.patient.repository.PatientRepository;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.patient.dto.TrashedPatientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deleting a patient moves them (and their cases) to a trash. For the retention window
 * ({@code app.patient.retention-days}, default 45) a super admin can restore them; after that the
 * nightly job erases them for good. Native repository queries on purpose: the normal entity queries hide deleted rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientTrashService {

    private final PatientRepository patients;
    private final CaseRepository cases;
    private final RecordPurger purger;

    @Value("${app.patient.retention-days:45}")
    private int retentionDays;

    public int getRetentionDays() {
        return retentionDays;
    }

    /** Moves a patient and their cases to the trash. The caller has already checked ownership. */
    @Transactional
    public void moveToTrash(UUID patientId) {
        cases.trashByPatientId(patientId);
        if (patients.trash(patientId) == 0) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
    }

    @Transactional(readOnly = true)
    public List<TrashedPatientResponse> listTrash() {
        return patients.findTrashed().stream().map(row -> {
            Instant deletedAt = toInstant(row.getDeletedAt());
            return new TrashedPatientResponse(
                    row.getId(), row.getPatientCode(), row.getFirstName(), row.getLastName(),
                    row.getHealerId(), row.getHealerName(),
                    deletedAt, deletedAt.plus(Duration.ofDays(retentionDays)), row.getCases().intValue());
        }).toList();
    }

    /** The driver hands the timestamp back as a java.sql.Timestamp, an Instant or an OffsetDateTime. */
    private static Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return (Instant) value;
    }

    /** Brings a trashed patient back with the cases that were removed along with them. */
    @Transactional
    public void restore(UUID patientId) {
        if (patients.restore(patientId) == 0) {
            throw new ResourceNotFoundException("Deleted patient", patientId);
        }
        cases.restoreByPatientId(patientId);
    }

    /** Nightly: erases every patient whose retention window has passed. */
    @Scheduled(cron = "${app.patient.purge.cron:0 30 2 * * *}")
    public void purgeExpired() {
        int purged = purgeExpired(Instant.now());
        if (purged > 0) {
            log.info("Erased {} patient(s) past the {}-day retention window", purged, retentionDays);
        }
    }

    /** @return how many patients were erased */
    public int purgeExpired(Instant now) {
        java.sql.Timestamp cutoff = java.sql.Timestamp.from(now.minus(Duration.ofDays(retentionDays)));
        List<UUID> expired = patients.findIdsTrashedBefore(cutoff);
        int purged = 0;
        for (UUID id : expired) {
            try {
                purger.purgePatient(id);
                purged++;
            } catch (RuntimeException e) {
                log.error("Could not erase patient {} - will retry tomorrow", id, e);
            }
        }
        return purged;
    }
}
