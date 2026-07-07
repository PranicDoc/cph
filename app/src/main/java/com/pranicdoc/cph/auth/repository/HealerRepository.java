package com.pranicdoc.cph.auth.repository;

import com.pranicdoc.cph.auth.model.Healer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealerRepository extends JpaRepository<Healer, UUID> {

    Optional<Healer> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByProviderUserId(String providerUserId);

    boolean existsByEmailAndClinicId(String email, UUID clinicId);

    @Query("""
           SELECT h.email
           FROM Healer h
           WHERE h.id = :healerId
       """)
    String findEmailById(UUID healerId);

    boolean existsByIdAndClinicId(UUID id, UUID clinicId);

    List<Healer> findAllByClinicId(UUID clinicId);
}
