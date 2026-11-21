package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.Declaration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeclarationRepository extends JpaRepository<Declaration, UUID> {

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM declaration_aud WHERE id IN (SELECT id FROM declaration WHERE healing_case_id IN (:ids))", nativeQuery = true)
    int eraseAuditByCaseIds(@Param("ids") Collection<UUID> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM declaration WHERE healing_case_id IN (:ids)", nativeQuery = true)
    int eraseByCaseIds(@Param("ids") Collection<UUID> ids);
}
