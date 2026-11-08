package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.common.enums.ColorUsageType;
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

    /** A code may exist once per category (V56), so this returns the first match, CLEANSING before ENERGISING. */
    Optional<PranaColorCatalog> findFirstByCodeOrderByCategoryAsc(String code);

    /** Name-based lookup for import-time resolve-or-backfill (source files carry display names, not codes). */
    Optional<PranaColorCatalog> findByNameIgnoreCase(String name);

    /** Live colors with this name and category (the uq_prana_color_catalog_name_category pair). */
    boolean existsByNameIgnoreCaseAndCategory(String name, ColorUsageType category);

    boolean existsByNameIgnoreCaseAndCategoryAndIdNot(String name, ColorUsageType category, UUID id);

    /** The uq_prana_color_catalog_code_category pair. */
    boolean existsByCodeIgnoreCaseAndCategoryAndIdNot(String code, ColorUsageType category, UUID id);

    boolean existsByCodeIgnoreCaseAndCategory(String code, ColorUsageType category);

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
