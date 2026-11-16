package com.pranicdoc.cph.healing.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.healing.model.ChakraReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChakraReadingRepository extends JpaRepository<ChakraReading, UUID> {

    List<ChakraReading> findByChakraScanId(UUID scanId);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM chakra_reading_aud WHERE id IN (SELECT id FROM chakra_reading WHERE chakra_scan_id IN (:ids))", nativeQuery = true)
    int eraseAuditByScanIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM chakra_reading WHERE chakra_scan_id IN (:ids)", nativeQuery = true)
    int eraseByScanIds(@Param("ids") Collection<UUID> ids);
}
