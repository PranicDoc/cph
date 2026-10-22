package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.common.enums.ProtocolType;
import com.pranicdoc.cph.masterdata.model.ProtocolCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * One page of the protocols a caller can see (system rows, plus the healer's own when
     * {@code healerId} is given), optionally of one type, whose name, book or chapter contains
     * {@code pattern} (already lower-cased and wrapped in {@code %}; empty matches everything).
     */
    @Query("""
            select p from ProtocolCatalog p
            where (p.healerId is null or p.healerId = :healerId)
              and (:type is null or p.protocolType = :type)
              and (:pattern = ''
                   or lower(p.name) like :pattern
                   or lower(coalesce(p.sourceBook, '')) like :pattern
                   or lower(coalesce(p.chapter, '')) like :pattern)
            """)
    Page<ProtocolCatalog> search(@Param("healerId") UUID healerId, @Param("type") ProtocolType type,
                                 @Param("pattern") String pattern, Pageable pageable);

    /** Upsert lookup for bulk import */
    Optional<ProtocolCatalog> findByNameAndSourceBookAndPageNumber(String name, String sourceBook, Integer pageNumber);

    /**
     * Lookup for chakra/color enrichment import (name + book, no page): may return more than
     * one row, since a protocol can legitimately span multiple pages under the same name and
     * book — name + sourceBook + pageNumber is the entity's actual unique key, not name + book
     * alone.
     */
    List<ProtocolCatalog> findByNameAndSourceBook(String name, String sourceBook);

    /**
     * Physically removes every row — including already soft-deleted ones, which the entity's
     * {@code @SQLRestriction} hides from JPQL/derived queries — via native SQL. Used only by
     * the SUPADM master-data wipe.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM protocol_catalog", nativeQuery = true)
    int hardDeleteAll();
}
