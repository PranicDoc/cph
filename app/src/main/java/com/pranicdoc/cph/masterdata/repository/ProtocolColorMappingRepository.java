package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolColorMappingRepository extends JpaRepository<ProtocolColorMapping, UUID> {

    List<ProtocolColorMapping> findByProtocolNameAndClinicId(String protocolName, UUID clinicId);

    List<ProtocolColorMapping> findByProtocolNameAndClinicIdIsNull(String protocolName);

    List<ProtocolColorMapping> findByClinicIdIsNull();

    List<ProtocolColorMapping> findByClinicIdIsNullOrClinicId(UUID clinicId);
}
