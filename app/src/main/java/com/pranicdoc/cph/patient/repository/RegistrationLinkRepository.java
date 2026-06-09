package com.pranicdoc.cph.patient.repository;

import com.pranicdoc.cph.patient.model.RegistrationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RegistrationLinkRepository extends JpaRepository<RegistrationLink, String> {

    /** First open: ACTIVE → IN_PROGRESS (not immediately consumed). */
    @Modifying
    @Query("""
        UPDATE RegistrationLink l
        SET l.status = 'IN_PROGRESS'
        WHERE l.uuid = :uuid AND l.status = 'ACTIVE' AND l.expiresAt > :now
    """)
    int consume(@Param("uuid") String uuid, @Param("now") Instant now);

    /** Re-auth: find a still-valid IN_PROGRESS link for a returning patient. */
    @Query("""
        SELECT l FROM RegistrationLink l
        WHERE l.uuid = :uuid AND l.status = 'IN_PROGRESS' AND l.expiresAt > :now
    """)
    Optional<RegistrationLink> findInProgress(@Param("uuid") String uuid, @Param("now") Instant now);

    /** Final consumption: called when the case is created — PATIENT_CASE token takes over. */
    @Modifying
    @Query("UPDATE RegistrationLink l SET l.status = 'USED' WHERE l.uuid = :uuid")
    void markUsed(@Param("uuid") String uuid);
}
