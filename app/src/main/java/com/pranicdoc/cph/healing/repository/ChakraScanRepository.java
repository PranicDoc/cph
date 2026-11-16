package com.pranicdoc.cph.healing.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.common.enums.ScanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChakraScanRepository extends JpaRepository<ChakraScan, UUID> {

    List<ChakraScan> findByHealingSessionId(UUID sessionId);

    Optional<ChakraScan> findByHealingSessionIdAndScanType(UUID sessionId, ScanType scanType);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM chakra_scan WHERE healing_session_id IN (:ids)", nativeQuery = true)
    List<UUID> findIdsBySessionIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM chakra_scan_aud WHERE id IN (:ids)", nativeQuery = true)
    int eraseAuditByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM chakra_scan WHERE id IN (:ids)", nativeQuery = true)
    int eraseByIds(@Param("ids") Collection<UUID> ids);
}
