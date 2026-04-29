package com.pranicdoc.cph.patient.repository;

import com.pranicdoc.cph.patient.model.RegistrationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RegistrationLinkRepository extends JpaRepository<RegistrationLink, String> {
    @Modifying
    @Query("""
        UPDATE RegistrationLink l
        SET l.status = 'USED'
        WHERE l.uuid = :uuid AND l.status = 'ACTIVE' AND l.expiresAt > :now
    """)
    int consume(@Param("uuid") String uuid, @Param("now") Instant now);
}
