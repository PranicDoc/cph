package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PranaColorCatalogRepository extends JpaRepository<PranaColorCatalog, UUID> {

    Optional<PranaColorCatalog> findByCode(String code);

    /** Name-based lookup for import-time resolve-or-backfill (source files carry display names, not codes). */
    Optional<PranaColorCatalog> findByNameIgnoreCase(String name);

    /** System colors (healerId is null) */
    List<PranaColorCatalog> findByHealerIdIsNull();

    /** Healer-custom colors */
    List<PranaColorCatalog> findByHealerId(UUID healerId);

    /** All accessible: system + current healer */
    List<PranaColorCatalog> findByHealerIdIsNullOrHealerId(UUID healerId);

    /**
     * Physically removes every row — including already soft-deleted ones, which the entity's
     * {@code @SQLRestriction} hides from JPQL/derived queries — via native SQL. Used only by
     * the SUPADM master-data wipe.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM prana_color_catalog", nativeQuery = true)
    int hardDeleteAll();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM prana_color_catalog_properties", nativeQuery = true)
    int hardDeleteAllProperties();
}
