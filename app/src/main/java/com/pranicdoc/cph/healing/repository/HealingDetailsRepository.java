package com.pranicdoc.cph.healing.repository;

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
}
