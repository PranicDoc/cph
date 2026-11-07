package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.HealerProtocolUnderstanding;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealerProtocolUnderstandingRepository extends JpaRepository<HealerProtocolUnderstanding, UUID> {

    Optional<HealerProtocolUnderstanding> findByHealingCaseId(UUID healingSessionId);

    /** The colour columns of a healer's other cases, newest first; pass a Pageable to cap the rows. */
    @Query("""
            select new com.pranicdoc.cph.healing.repository.HealerProtocolUnderstandingRepository$ColorsOnly(
                h.healingCaseId, h.cleansingColorsJson, h.energisingColorsJson)
            from HealerProtocolUnderstanding h
            where h.healerId = :healerId and h.healingCaseId <> :caseId
            order by h.completionDate desc
            """)
    List<ColorsOnly> findColorsByHealerIdExcludingCase(@Param("healerId") UUID healerId,
                                                       @Param("caseId") UUID caseId, Pageable page);

    record ColorsOnly(UUID healingCaseId, String cleansingColorsJson, String energisingColorsJson) {}
}
