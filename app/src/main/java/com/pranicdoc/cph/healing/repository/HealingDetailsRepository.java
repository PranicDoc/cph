package com.pranicdoc.cph.healing.repository;

import java.util.Collection;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.analytics.dto.DashboardTopProtocol;
import com.pranicdoc.cph.healing.model.HealingDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface HealingDetailsRepository extends JpaRepository<HealingDetails, UUID> {

    @Query("""
        SELECT new com.pranicdoc.cph.analytics.dto.DashboardTopProtocol(hd.protocolName, COUNT(hd))
        FROM HealingDetails hd
        JOIN hd.healingCase hc
        WHERE hc.healerId = :healerId
          AND hd.protocolName IS NOT NULL
          AND hd.protocolName <> ''
          AND hc.createdAt >= :fromInstant
          AND hc.createdAt < :toInstant
        GROUP BY hd.protocolName
        ORDER BY COUNT(hd) DESC
    """)
    List<DashboardTopProtocol> getTopProtocols(
            @Param("healerId") UUID healerId,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            Pageable pageable);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM healing_details WHERE healing_case_id IN (:ids)", nativeQuery = true)
    List<UUID> findIdsByCaseIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM healing_details_aud WHERE id IN (:ids)", nativeQuery = true)
    int eraseAuditByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM healing_details WHERE id IN (:ids)", nativeQuery = true)
    int eraseByIds(@Param("ids") Collection<UUID> ids);
}
