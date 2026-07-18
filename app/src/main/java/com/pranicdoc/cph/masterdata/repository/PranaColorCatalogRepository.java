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

    /** System colors (healerId is null) */
    List<PranaColorCatalog> findByHealerIdIsNull();

    /** Healer-custom colors */
    List<PranaColorCatalog> findByHealerId(UUID healerId);

    /** All accessible: system + current healer */
    List<PranaColorCatalog> findByHealerIdIsNullOrHealerId(UUID healerId);
}
