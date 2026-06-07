package com.pranicdoc.cph.notification.repository;

import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackLinkRepository extends JpaRepository<FeedbackLink, String> {

    Optional<FeedbackLink> findByTokenAndStatusAndExpiresAtAfter(
            String token, LinkStatus status, Instant now);

    @Modifying
    @Query("UPDATE FeedbackLink fl SET fl.status = :used " +
           "WHERE fl.caseId = :caseId AND fl.status = :active")
    int markUsedBySessionId(@Param("caseId") UUID sessionId,
                            @Param("used") LinkStatus used,
                            @Param("active") LinkStatus active);
}
