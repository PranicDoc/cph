package com.pranicdoc.cph.masterdata.repository;

import com.pranicdoc.cph.masterdata.model.MasterDataElevation;
import com.pranicdoc.cph.rbac.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MasterDataElevationRepository extends JpaRepository<MasterDataElevation, UUID> {

    boolean existsByHealerIdAndResourceAndExpiresAtAfter(UUID healerId, Resource resource, Instant now);

    List<MasterDataElevation> findByHealerIdAndExpiresAtAfter(UUID healerId, Instant now);
}
