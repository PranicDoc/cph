package com.pranicdoc.cph.notification.repository;

import com.pranicdoc.cph.common.enums.LinkStatus;
import com.pranicdoc.cph.notification.entity.FeedbackLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    /** True when the case has an unexpired link of the given status flagged as the final-feedback link. */
    @Query("SELECT COUNT(fl) > 0 FROM FeedbackLink fl " +
           "WHERE fl.caseId = :caseId AND fl.isFinal = true " +
           "AND fl.status = :status AND fl.expiresAt > :now")
    boolean existsActiveFinalLink(@Param("caseId") UUID caseId,
                                  @Param("status") LinkStatus status,
                                  @Param("now") Instant now);

    @Query(nativeQuery = true, value = """
        SELECT hc.id         AS caseId,
               p.first_name  AS patientFirstName,
               p.last_name   AS patientLastName,
               fl.token      AS feedbackToken,
               fl.created_at AS sentAt,
               fl.is_final   AS isFinal
        FROM feedback_link fl
        JOIN healing_case hc ON hc.id = fl.case_id
        JOIN patient p ON p.id = hc.patient_id
        WHERE hc.healer_id = CAST(:healerId AS uuid)
          AND fl.status     = 'ACTIVE'
          AND fl.created_at <= :sentBefore
          AND p.is_deleted  = false
          AND hc.is_deleted = false
        ORDER BY fl.is_final DESC, fl.created_at ASC
    """)
    List<FollowupPoint> getFollowupsForQueue(
            @Param("healerId") UUID healerId,
            @Param("sentBefore") Instant sentBefore);

    interface FollowupPoint {
        UUID getCaseId();
        String getPatientFirstName();
        String getPatientLastName();
        String getFeedbackToken();
        Instant getSentAt();
        boolean getIsFinal();
    }
}
