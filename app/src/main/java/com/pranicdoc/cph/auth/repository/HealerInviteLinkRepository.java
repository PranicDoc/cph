package com.pranicdoc.cph.auth.repository;

import com.pranicdoc.cph.auth.model.HealerInviteLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface HealerInviteLinkRepository extends JpaRepository<HealerInviteLink, UUID> {

    Optional<HealerInviteLink> findByTokenAndStatusAndExpiresAtAfter(
            String token, HealerInviteLink.Status status, Instant now);

    Optional<HealerInviteLink> findByEmailAndClinicIdAndStatus(
            String email, UUID clinicId, HealerInviteLink.Status status);

    /**
     * D-09: find a pending self-signup token for an email, so resubmission
     * can rotate it in place instead of creating a duplicate row.
     */
    Optional<HealerInviteLink> findByEmailAndSourceAndStatus(
            String email, HealerInviteLink.Source source, HealerInviteLink.Status status);

    /**
     * D-10: detect a pending admin invite for an email so self-signup can
     * silently no-op (generic success, no email sent) instead of racing it.
     */
    boolean existsByEmailAndSourceAndStatus(
            String email, HealerInviteLink.Source source, HealerInviteLink.Status status);

    Page<HealerInviteLink> findByClinicId(UUID clinicId, Pageable pageable);

    /**
     * Conditional single-use race guard (T-22-04/05): flips PENDING → ACCEPTED
     * only if the row is still PENDING. Returns 0 if a concurrent accept already
     * won the race, in which case the caller must reject with TokenExpiredException.
     */
    @Modifying
    @Query("""
        UPDATE HealerInviteLink l
        SET l.status = 'ACCEPTED', l.acceptedAt = :now
        WHERE l.id = :id AND l.status = 'PENDING'
    """)
    int markAccepted(@Param("id") UUID id, @Param("now") Instant now);
}
