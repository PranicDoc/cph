package com.pranicdoc.cph.masterdata.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
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

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM master_data_elevation WHERE healer_id = :healerId", nativeQuery = true)
    int eraseByHealerId(@Param("healerId") UUID healerId);
}
