package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.TreatmentStep;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TreatmentStepRepository extends JpaRepository<TreatmentStep, UUID> {

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM treatment_step WHERE healing_details_id IN (:ids)", nativeQuery = true)
    List<UUID> findIdsByHealingDetailsIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM treatment_step_targets WHERE treatment_step_id IN (:ids)", nativeQuery = true)
    int eraseTargetsByStepIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM treatment_step_aud WHERE id IN (:ids)", nativeQuery = true)
    int eraseAuditByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM treatment_step WHERE id IN (:ids)", nativeQuery = true)
    int eraseByIds(@Param("ids") Collection<UUID> ids);
}
