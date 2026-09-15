package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorView;
import com.pranicdoc.cph.masterdata.model.ProtocolChakraColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolChakraColorRepository extends JpaRepository<ProtocolChakraColor, UUID> {

    /** Bulk prefetch for import — avoids one existence query per (protocolChakra, color, usageType) row. */
    List<ProtocolChakraColor> findByProtocolChakra_IdIn(List<UUID> protocolChakraIds);

    /**
     * Colors (cleansing + energizing, distinguished by {@code usageType})
     * for the given protocol+chakra combinations (step 3 of the read flow).
     * A lean projection query — no entity graph fetch — matching what the
     * caller actually needs: {@code colorId} + which usage it applies to.
     */
    @Query("""
            select new com.pranicdoc.cph.masterdata.dto.ProtocolChakraColorView(
                pc.protocol.id, pc.chakra.id, pcc.color.id, pcc.usageType)
            from ProtocolChakraColor pcc
            join pcc.protocolChakra pc
            where pc.protocol.id in :protocolIds and pc.chakra.id in :chakraIds
            """)
    List<ProtocolChakraColorView> findColorsForProtocolsAndChakras(
            @Param("protocolIds") List<UUID> protocolIds,
            @Param("chakraIds") List<UUID> chakraIds);
}
