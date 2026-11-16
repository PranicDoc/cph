package com.pranicdoc.cph.masterdata.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.masterdata.model.ProtocolImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolImportJobRepository extends JpaRepository<ProtocolImportJob, UUID> {

    /** A healer's own import jobs, newest first — the "Imports" tab's list. */
    List<ProtocolImportJob> findByHealerIdOrderByCreatedAtDesc(UUID healerId);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM protocol_import_job WHERE healer_id = :healerId", nativeQuery = true)
    int eraseByHealerId(@Param("healerId") UUID healerId);
}
