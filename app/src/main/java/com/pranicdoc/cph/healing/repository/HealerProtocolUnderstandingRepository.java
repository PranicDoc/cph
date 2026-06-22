package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.HealerProtocolUnderstanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealerProtocolUnderstandingRepository extends JpaRepository<HealerProtocolUnderstanding, UUID> {

    Optional<HealerProtocolUnderstanding> findByHealingCaseId(UUID healingSessionId);
}
