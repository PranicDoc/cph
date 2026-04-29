package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.common.enums.CaseStatus;
import com.pranicdoc.cph.healing.model.HealingCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<HealingCase, UUID> {

    Page<HealingCase> findByPatientId(UUID patientId, Pageable pageable);

    Page<HealingCase> findByHealerId(UUID healerId, Pageable pageable);

    boolean existsByPatientIdAndClinicIdAndStatus(UUID uuid, UUID currentClinicId, CaseStatus caseStatus);
}
