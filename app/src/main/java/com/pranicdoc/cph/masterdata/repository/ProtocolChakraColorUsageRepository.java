package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ProtocolChakraColorUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolChakraColorUsageRepository extends JpaRepository<ProtocolChakraColorUsage, UUID> {

    List<ProtocolChakraColorUsage> findByProtocolNameAndHealerId(String protocolName, UUID healerId);

    List<ProtocolChakraColorUsage> findByProtocolNameAndHealerIdIsNull(String protocolName);

    List<ProtocolChakraColorUsage> findByHealerIdIsNull();

    List<ProtocolChakraColorUsage> findByHealerIdIsNullOrHealerId(UUID healerId);
}
