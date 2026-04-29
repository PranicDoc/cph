package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.ProtocolEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolEntryRepository extends JpaRepository<ProtocolEntry, UUID> {

    List<ProtocolEntry> findByHealingSessionId(UUID sessionId);
}
