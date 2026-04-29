package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.ProtocolColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolColorRepository extends JpaRepository<ProtocolColor, UUID> {

    List<ProtocolColor> findByProtocolEntryId(UUID entryId);
}
