package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.HealingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<HealingSession, UUID> {

    List<HealingSession> findByHealingCaseIdOrderBySessionNumberAsc(UUID healingCaseId);
}
