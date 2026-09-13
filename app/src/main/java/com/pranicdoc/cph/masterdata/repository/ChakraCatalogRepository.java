package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChakraCatalogRepository extends JpaRepository<ChakraCatalog, UUID> {

    Optional<ChakraCatalog> findByCode(String code);

    /**
     * Matches by descriptive name (case-insensitive) rather than by short
     * code — used to resolve/auto-create chakras from free-text names
     * ("Solar Plexus Chakra") encountered during chakra/color enrichment
     * import (MDIMPORT-22).
     */
    Optional<ChakraCatalog> findByNameIgnoreCase(String name);

    /** System chakras (healerId is null) */
    List<ChakraCatalog> findByHealerIdIsNull();

    /** Healer-custom chakras */
    List<ChakraCatalog> findByHealerId(UUID healerId);

    /** All accessible: system + current healer */
    List<ChakraCatalog> findByHealerIdIsNullOrHealerId(UUID healerId);
}
