package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.CaseMedicalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseMedicalSnapshotRepository extends JpaRepository<CaseMedicalSnapshot, UUID> {

    /**
     * The case's snapshot row even if soft-deleted (native SQL is not filtered by @SQLRestriction), so an
     * upsert restores it instead of inserting a second row and hitting uq_case_medical_snapshot_case.
     */
    @Query(value = "SELECT * FROM case_medical_snapshot WHERE healing_case_id = :caseId", nativeQuery = true)
    Optional<CaseMedicalSnapshot> findIncludingDeletedByHealingCaseId(@Param("caseId") UUID caseId);
}
