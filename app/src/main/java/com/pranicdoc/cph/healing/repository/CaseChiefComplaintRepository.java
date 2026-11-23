package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.CaseChiefComplaint;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseChiefComplaintRepository extends JpaRepository<CaseChiefComplaint, UUID> {

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM case_chief_complaint_aud WHERE id IN (SELECT id FROM case_chief_complaint WHERE healing_case_id IN (:ids))", nativeQuery = true)
    int eraseAuditByCaseIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM case_chief_complaint WHERE healing_case_id IN (:ids)", nativeQuery = true)
    int eraseByCaseIds(@Param("ids") Collection<UUID> ids);
}
