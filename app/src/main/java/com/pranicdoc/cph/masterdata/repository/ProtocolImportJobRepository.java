package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.ProtocolImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocolImportJobRepository extends JpaRepository<ProtocolImportJob, UUID> {

    /** A healer's own import jobs, newest first — the "Imports" tab's list. */
    List<ProtocolImportJob> findByHealerIdOrderByCreatedAtDesc(UUID healerId);
}
