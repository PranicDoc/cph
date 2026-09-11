package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.common.enums.MasterdataType;
import com.pranicdoc.cph.masterdata.model.MasterdataColumnConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MasterdataColumnConfigRepository extends JpaRepository<MasterdataColumnConfig, UUID> {

    /** Per-type ordered list for the column-config admin view and Phase 02's importers. */
    List<MasterdataColumnConfig> findAllByMasterdataTypeOrderByColumnNameAsc(MasterdataType masterdataType);

    /** D-05-style collision rejection — proactive service-layer check before a create attempt hits the DB UNIQUE constraint. */
    boolean existsByMasterdataTypeAndColumnName(MasterdataType masterdataType, String columnName);
}
