package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolColorMappingRepository extends JpaRepository<ProtocolColorMapping, UUID> {

    List<ProtocolColorMapping> findByProtocolNameAndHealerId(String protocolName, UUID healerId);

    List<ProtocolColorMapping> findByProtocolNameAndHealerIdIsNull(String protocolName);

    List<ProtocolColorMapping> findByHealerIdIsNull();

    List<ProtocolColorMapping> findByHealerIdIsNullOrHealerId(UUID healerId);
}
