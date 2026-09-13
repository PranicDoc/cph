package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.PranaColorCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PranaColorCatalogRepository extends JpaRepository<PranaColorCatalog, UUID> {

    Optional<PranaColorCatalog> findByCode(String code);

    /**
     * Matches by descriptive name (case-insensitive) rather than by short
     * code — used to resolve/auto-create colors from free-text phrases
     * ("Light Whitish Green") that don't correspond to any existing base
     * color code (MDIMPORT-22).
     */
    Optional<PranaColorCatalog> findByNameIgnoreCase(String name);

    /** System colors (healerId is null) */
    List<PranaColorCatalog> findByHealerIdIsNull();

    /** Healer-custom colors */
    List<PranaColorCatalog> findByHealerId(UUID healerId);

    /** All accessible: system + current healer */
    List<PranaColorCatalog> findByHealerIdIsNullOrHealerId(UUID healerId);
}
