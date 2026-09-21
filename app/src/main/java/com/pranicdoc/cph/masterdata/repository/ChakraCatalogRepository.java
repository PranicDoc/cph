package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChakraCatalogRepository extends JpaRepository<ChakraCatalog, UUID> {

    Optional<ChakraCatalog> findByCode(String code);

    /** Name-based lookup for import-time resolve-or-backfill (source files carry display names, not codes). */
    Optional<ChakraCatalog> findByNameIgnoreCase(String name);

    /** System chakras (healerId is null) */
    List<ChakraCatalog> findByHealerIdIsNull();

    /** Healer-custom chakras */
    List<ChakraCatalog> findByHealerId(UUID healerId);

    /** All accessible: system + current healer */
    List<ChakraCatalog> findByHealerIdIsNullOrHealerId(UUID healerId);

    /**
     * Physically removes every row — including already soft-deleted ones, which the entity's
     * {@code @SQLRestriction} hides from JPQL/derived queries — via native SQL. Used only by
     * the SUPADM master-data wipe.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM chakra_catalog", nativeQuery = true)
    int hardDeleteAll();
}
