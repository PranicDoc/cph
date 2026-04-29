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

    /** System colors (clinicId is null) */
    List<PranaColorCatalog> findByClinicIdIsNull();

    /** Clinic-custom colors */
    List<PranaColorCatalog> findByClinicId(UUID clinicId);

    /** All accessible: system + current clinic */
    List<PranaColorCatalog> findByClinicIdIsNullOrClinicId(UUID clinicId);
}
