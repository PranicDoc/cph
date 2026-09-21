package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ChakraCatalog;
import com.pranicdoc.cph.masterdata.model.ProtocolChakra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * Distinct chakras associated with any of the given protocols, visible to
     * the given healer (system rows, healer_id null, union that healer's own
     * rows) — step 2 of the protocol -> chakra -> color read flow used by the
     * healing-session wizard. Returns {@link ChakraCatalog} rows directly
     * rather than the join entity — the caller needs chakra master data
     * (name/code/category), not the link itself.
     */
    @Query("""
            select distinct pc.chakra from ProtocolChakra pc
            where pc.protocol.id in :protocolIds
              and (pc.healerId is null or pc.healerId = :healerId)
            """)
    List<ChakraCatalog> findDistinctChakrasByProtocolIdsForHealer(
            @Param("protocolIds") List<UUID> protocolIds, @Param("healerId") UUID healerId);

    /** A specific healer's own link for this protocol+chakra pair (find-or-create target for the add endpoint). */
    Optional<ProtocolChakra> findByProtocol_IdAndChakra_IdAndHealerId(UUID protocolId, UUID chakraId, UUID healerId);

    /**
     * All links (system + the given healer's own) for one protocol — the
     * single-protocol "manage chakras/colors" admin view.
     */
    List<ProtocolChakra> findByProtocol_IdAndHealerIdIsNullOrHealerId(UUID protocolId, UUID healerId);

    /**
     * Physically removes every row — including already soft-deleted ones, which the entity's
     * {@code @SQLRestriction} hides from JPQL/derived queries — via native SQL. Used only by
     * the SUPADM master-data wipe.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM protocol_chakra", nativeQuery = true)
    int hardDeleteAll();
}
