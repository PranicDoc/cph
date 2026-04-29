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

    /** System chakras (clinicId is null) */
    List<ChakraCatalog> findByClinicIdIsNull();

    /** Clinic-custom chakras */
    List<ChakraCatalog> findByClinicId(UUID clinicId);

    /** All accessible: system + current clinic */
    List<ChakraCatalog> findByClinicIdIsNullOrClinicId(UUID clinicId);
}
