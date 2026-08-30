package com.pranicdoc.cph.auth.repository;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.common.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
