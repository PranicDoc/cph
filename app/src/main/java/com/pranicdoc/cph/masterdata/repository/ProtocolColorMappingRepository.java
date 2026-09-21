package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ProtocolColorMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolColorMappingRepository extends JpaRepository<ProtocolColorMapping, UUID> {

    List<ProtocolColorMapping> findByProtocolNameAndHealerId(String protocolName, UUID healerId);

    List<ProtocolColorMapping> findByProtocolNameAndHealerIdIsNull(String protocolName);

    List<ProtocolColorMapping> findByHealerIdIsNull();

    List<ProtocolColorMapping> findByHealerIdIsNullOrHealerId(UUID healerId);

    /**
     * Physically removes every row — including already soft-deleted ones, which the entity's
     * {@code @SQLRestriction} hides from JPQL/derived queries — via native SQL. Used only by
     * the SUPADM master-data wipe.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM protocol_color_mapping", nativeQuery = true)
    int hardDeleteAll();
}
