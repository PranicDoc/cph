package com.pranicdoc.cph.auth.repository;

import com.pranicdoc.cph.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);

    /** True if a link was issued to this healer since {@code since}: the per-account email throttle. */
    boolean existsByHealerIdAndCreatedAtAfter(UUID healerId, Instant since);

    /** Spends a token only if it is still unspent; 0 means a concurrent request already used it. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** Spends every unspent token of a healer, so only the newest link (or none, after a reset) works. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.healerId = :healerId AND t.usedAt IS NULL")
    int invalidateOutstanding(@Param("healerId") UUID healerId, @Param("now") Instant now);
}
