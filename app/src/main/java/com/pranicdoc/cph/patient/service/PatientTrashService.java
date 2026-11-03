package com.pranicdoc.cph.patient.service;

import com.pranicdoc.cph.common.audit.Auditable;
import com.pranicdoc.cph.common.exception.ResourceNotFoundException;
import com.pranicdoc.cph.patient.dto.TrashedPatientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deleting a patient moves them (and their cases) to a trash. For the retention window
 * ({@code app.patient.retention-days}, default 45) a super admin can restore them; after that the
 * nightly job erases them for good. Plain SQL on purpose: the normal entity queries hide deleted rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatientTrashService {

    private final JdbcTemplate jdbc;
    private final RecordPurger purger;

    @Value("${app.patient.retention-days:45}")
    private int retentionDays;

    public int getRetentionDays() {
        return retentionDays;
    }

    /** Moves a patient and their cases to the trash. The caller has already checked ownership. */
    @Transactional
    public void moveToTrash(UUID patientId) {
        jdbc.update("UPDATE healing_case SET is_deleted = true, deleted_at = now(), deleted_with_patient = true, "
                + "updated_at = now() WHERE patient_id = ? AND is_deleted = false", patientId);
        int moved = jdbc.update("UPDATE patient SET is_deleted = true, deleted_at = now(), updated_at = now() "
                + "WHERE id = ? AND is_deleted = false", patientId);
        if (moved == 0) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
    }

    @Transactional(readOnly = true)
    public List<TrashedPatientResponse> listTrash() {
        return jdbc.query("""
                SELECT p.id, p.patient_code, p.first_name, p.last_name, p.healer_id, h.full_name, p.deleted_at,
                       (SELECT count(*) FROM healing_case c WHERE c.patient_id = p.id AND c.deleted_with_patient) AS cases
                  FROM patient p LEFT JOIN healer h ON h.id = p.healer_id
                 WHERE p.is_deleted AND p.deleted_at IS NOT NULL
                 ORDER BY p.deleted_at DESC
                """, (rs, i) -> {
            Instant deletedAt = rs.getTimestamp("deleted_at").toInstant();
            return new TrashedPatientResponse(
                    rs.getObject("id", UUID.class), rs.getString("patient_code"),
                    rs.getString("first_name"), rs.getString("last_name"),
                    rs.getObject("healer_id", UUID.class), rs.getString("full_name"),
                    deletedAt, deletedAt.plus(Duration.ofDays(retentionDays)), rs.getInt("cases"));
        });
    }

    /** Brings a trashed patient back with the cases that were removed along with them. */
    @Transactional
    public void restore(UUID patientId) {
        int restored = jdbc.update("UPDATE patient SET is_deleted = false, deleted_at = NULL, updated_at = now() "
                + "WHERE id = ? AND is_deleted = true AND deleted_at IS NOT NULL", patientId);
        if (restored == 0) {
            throw new ResourceNotFoundException("Deleted patient", patientId);
        }
        jdbc.update("UPDATE healing_case SET is_deleted = false, deleted_at = NULL, deleted_with_patient = false, "
                + "updated_at = now() WHERE patient_id = ? AND deleted_with_patient", patientId);
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
        Timestamp cutoff = Timestamp.from(now.minus(Duration.ofDays(retentionDays)));
        List<UUID> expired = jdbc.queryForList(
                "SELECT id FROM patient WHERE is_deleted AND deleted_at IS NOT NULL AND deleted_at < ?",
                UUID.class, cutoff);
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
