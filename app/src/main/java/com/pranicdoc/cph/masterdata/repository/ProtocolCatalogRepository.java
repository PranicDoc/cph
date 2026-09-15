package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProtocolCatalogRepository extends JpaRepository<ProtocolCatalog, UUID> {

    /** System protocols (healerId is null) */
    List<ProtocolCatalog> findByHealerIdIsNull();

    /** All accessible: system + current healer */
    List<ProtocolCatalog> findByHealerIdIsNullOrHealerId(UUID healerId);

    /** System protocols of a given type */
    List<ProtocolCatalog> findByProtocolTypeAndHealerIdIsNull(ProtocolType protocolType);

    /** All accessible protocols of a given type: system + current healer */
    List<ProtocolCatalog> findByProtocolTypeAndHealerIdIsNullOrHealerId(ProtocolType protocolType, UUID healerId);

    /** Upsert lookup for bulk import */
    Optional<ProtocolCatalog> findByNameAndSourceBookAndPageNumber(String name, String sourceBook, Integer pageNumber);

    /**
     * Lookup for chakra/color enrichment import (name + book, no page): may return more than
     * one row, since a protocol can legitimately span multiple pages under the same name and
     * book — name + sourceBook + pageNumber is the entity's actual unique key, not name + book
     * alone.
     */
    List<ProtocolCatalog> findByNameAndSourceBook(String name, String sourceBook);
}
