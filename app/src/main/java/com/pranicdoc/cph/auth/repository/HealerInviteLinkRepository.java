package com.pranicdoc.cph.auth.repository;

import java.util.List;
import java.util.Collection;
import com.pranicdoc.cph.auth.model.HealerInviteLink;
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

    Optional<HealerInviteLink> findByEmailAndStatus(
            String email, HealerInviteLink.Status status);

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

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE healer_invite_links SET invited_by_healer_id = NULL WHERE invited_by_healer_id = :healerId", nativeQuery = true)
    int clearInviter(@Param("healerId") UUID healerId);
}
