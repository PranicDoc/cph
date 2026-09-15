package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProtocolChakraRepository extends JpaRepository<ProtocolChakra, UUID> {

    /** Duplicate-link check / single lookup for import upsert. */
    Optional<ProtocolChakra> findByProtocol_IdAndChakra_Id(UUID protocolId, UUID chakraId);

    /** Bulk prefetch for import — avoids one existence query per (protocol, chakra) row. */
    List<ProtocolChakra> findByProtocol_IdIn(List<UUID> protocolIds);

    /**
     * Distinct chakras associated with any of the given protocols (step 2 of
     * the protocol -> chakra -> color read flow). Returns {@link ChakraCatalog}
     * rows directly rather than the join entity — the caller needs chakra
     * master data (name/code/category), not the link itself.
     */
    @Query("select distinct pc.chakra from ProtocolChakra pc where pc.protocol.id in :protocolIds")
    List<ChakraCatalog> findDistinctChakrasByProtocolIds(@Param("protocolIds") List<UUID> protocolIds);
}
