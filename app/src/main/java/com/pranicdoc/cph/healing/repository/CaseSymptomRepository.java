package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.CaseSymptom;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseSymptomRepository extends JpaRepository<CaseSymptom, UUID> {

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM case_symptom WHERE healing_case_id IN (:ids)", nativeQuery = true)
    List<UUID> findIdsByCaseIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM case_symptom_aud WHERE id IN (:ids)", nativeQuery = true)
    int eraseAuditByIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM case_symptom WHERE id IN (:ids)", nativeQuery = true)
    int eraseByIds(@Param("ids") Collection<UUID> ids);
}
