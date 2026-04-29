package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.ChakraScan;
import com.pranicdoc.cph.common.enums.ScanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChakraScanRepository extends JpaRepository<ChakraScan, UUID> {

    List<ChakraScan> findByHealingSessionId(UUID sessionId);

    Optional<ChakraScan> findByHealingSessionIdAndScanType(UUID sessionId, ScanType scanType);
}
