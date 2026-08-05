package com.pranicdoc.cph.consent.repository;

import com.pranicdoc.cph.consent.model.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentTypeRepository extends JpaRepository<ConsentType, UUID> {

    /** Unfiltered lookup by key — used where an inactive type still needs to be resolvable (e.g. admin "Show inactive" detail views). */
    Optional<ConsentType> findByKey(String key);

    /** publish() existence guard (T-31.1-02) — a phantom or deactivated key must never reach a write. */
    Optional<ConsentType> findByKeyAndActiveTrue(String key);

    /** D-05 collision rejection — proactive service-layer check before a create attempt hits the DB UNIQUE constraint. */
    boolean existsByKey(String key);

    /** Registry-driven iteration for activePolicyVersions()/outstandingConsents() — ORDER BY id is a valid chronological ordering because BaseEntity assigns time-ordered UUIDv7 and V27's seeds use fixed low literals that sort first. */
    List<ConsentType> findAllByActiveTrueOrderByIdAsc();

    /** The admin "Show inactive" list (D-08) — every type, active or not, in the same chronological order. */
    List<ConsentType> findAllByOrderByIdAsc();
}
