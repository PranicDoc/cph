package com.pranicdoc.cph.healing.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.healing.model.ProtocolColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolColorRepository extends JpaRepository<ProtocolColor, UUID> {

    List<ProtocolColor> findByProtocolEntryId(UUID entryId);

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM protocol_color_aud WHERE id IN (SELECT id FROM protocol_color WHERE protocol_entry_id IN (:ids))", nativeQuery = true)
    int eraseAuditByEntryIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM protocol_color WHERE protocol_entry_id IN (:ids)", nativeQuery = true)
    int eraseByEntryIds(@Param("ids") Collection<UUID> ids);
}
