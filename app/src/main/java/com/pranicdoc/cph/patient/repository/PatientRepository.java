package com.pranicdoc.cph.patient.repository;

import com.pranicdoc.cph.patient.dto.PatientCaseResponse;
import com.pranicdoc.cph.patient.dto.PatientResponse;
import com.pranicdoc.cph.patient.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /**
     * Search patients by name (first or last, case-insensitive).
     * Used for autocomplete on the frontend.
     */
    @Query("SELECT p FROM Patient p WHERE " +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Patient> searchByName(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT new com.pranicdoc.cph.patient.dto.PatientCaseResponse(
            p.id,
            p.firstName,
            p.lastName,
            p.dob,
            p.gender,
            p.maritalStatus,
            p.email,
            p.contact,
            p.occupation,
            p.addressLine1,
            p.addressLine2,
            p.city,
            p.state,
            p.zip,
            p.createdAt,
            p.updatedAt,
            hc.status,
            hc.id
        )
        FROM Patient p
        LEFT JOIN HealingCase hc
            ON hc.patientId = p.id
            AND hc.createdAt = (
                SELECT MAX(hc2.createdAt)
                FROM HealingCase hc2
                WHERE hc2.patientId = p.id
            )
    """)
    List<PatientCaseResponse> fetchPatientsWithLatestCaseStatus();
}
