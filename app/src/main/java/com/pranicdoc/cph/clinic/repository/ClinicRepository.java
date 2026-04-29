package com.pranicdoc.cph.clinic.repository;

import com.pranicdoc.cph.clinic.model.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    Optional<Clinic> findByCode(String code);

    boolean existsByCode(String code);
}
