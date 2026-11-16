package com.pranicdoc.cph.auth.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.common.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealerRepository extends JpaRepository<Healer, UUID> {

    Optional<Healer> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByProviderUserId(String providerUserId);

    @Query("""
           SELECT h.email
           FROM Healer h
           WHERE h.id = :healerId
       """)
    String findEmailById(UUID healerId);

    @Query("""
           SELECT h.fullName
           FROM Healer h
           WHERE h.id = :healerId
       """)
    String findFullNameById(UUID healerId);

    /**
     * Count healers holding the given role. {@code @SQLRestriction("is_deleted = false")} on
     * {@link Healer} (and {@code BaseEntity}) already scopes every derived query — including
     * {@code countBy*} — to non-deleted rows, so this is the count of <em>live</em> accounts
     * with that role. Used by the last-SUPADM guard in {@code HealerServiceImpl.removeHealer}.
     */
    long countByRole(Role role);

    /** Active (non-deleted) healer accounts. */
    List<Healer> findByActiveTrue();

    /**
     * Active, non-deleted HEALER accounts that currently hold no live role
     * assignment at all — the target set for the baseline "Practitioner"
     * backfill ({@code POST /api/admin/roles/practitioner/backfill}).
     *
     * <p>Native because it reaches across to {@code user_role}, which has no
     * JPA relationship mapped from {@link Healer} (the principal column is
     * polymorphic, D-08). {@code ur.is_deleted = false} scopes the anti-join
     * to live assignments so a healer whose only assignment was later removed
     * is still picked up.
     */
    @Query(value = """
            SELECT h.id
            FROM healer h
            WHERE h.active = true
              AND h.is_deleted = false
              AND h.role = 'HEALER'
              AND NOT EXISTS (
                    SELECT 1 FROM user_role ur
                    WHERE ur.principal_type = 'HEALER'
                      AND ur.principal_id = h.id
                      AND ur.is_deleted = false
              )
            """, nativeQuery = true)
    List<UUID> findActiveHealerIdsWithNoLiveRole();

    // --- erase / purge (native: must also see soft-deleted rows and the _aud shadow tables) ---
    // Ids are selected as text and converted to UUID: a native UUID column comes back as byte[] on H2.

    @Query(value = "SELECT email AS \"email\", role AS \"role\" FROM healer WHERE id = :id", nativeQuery = true)
    List<HealerIdentity> findIdentity(@Param("id") UUID id);

    @Query(value = "SELECT CAST(id AS VARCHAR(36)) FROM healer WHERE role = 'SUPADM' AND is_deleted = false AND id <> :except ORDER BY created_at LIMIT 1", nativeQuery = true)
    List<UUID> findOtherSuperAdminIds(@Param("except") UUID except);

    /** The healer's role assignments live in the rbac module (derived-queries-only), so they are erased from here. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM user_role WHERE principal_id = :id", nativeQuery = true)
    int eraseUserRolesById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM healer_aud WHERE id = :id", nativeQuery = true)
    int eraseAuditById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM healer WHERE id = :id", nativeQuery = true)
    int eraseById(@Param("id") UUID id);

    /** E-mail and role of a healer, soft-deleted ones included. */
    interface HealerIdentity {
        String getEmail();
        String getRole();
    }
}
