package com.pranicdoc.cph.notification.repository;

import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.entity.NotificationOutbox.NotificationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    /**
     * Atomically takes ownership of one row for sending: PENDING, FAILED, or PROCESSING that has been
     * stuck since before {@code stuckBefore}. Returns 1 if the caller now owns the row, 0 if someone else
     * does (or it needs no work) — so the eager path and the sweeper can race safely. Reclaiming a stuck
     * row counts as an attempt (the instance that held it may have died mid-send).
     */
    default int claim(UUID id, Instant now, Instant stuckBefore, int maxRetries) {
        return claim(id, now, stuckBefore, maxRetries,
                NotificationStatus.PENDING, NotificationStatus.FAILED, NotificationStatus.PROCESSING);
    }

    default List<UUID> findSweepCandidates(Instant pendingBefore, Instant stuckBefore, int maxRetries, int batchSize) {
        return findSweepCandidates(pendingBefore, stuckBefore, maxRetries,
                NotificationStatus.PENDING, NotificationStatus.FAILED, NotificationStatus.PROCESSING,
                Limit.of(batchSize));
    }

    default int markExhaustedStuckAsDlq(Instant now, Instant stuckBefore, int maxRetries) {
        return markExhaustedStuckAsDlq(now, stuckBefore, maxRetries,
                NotificationStatus.PROCESSING, NotificationStatus.DLQ);
    }

    // SET expressions all read the row's pre-update values, so the CASE sees the old status.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.retryCount = CASE WHEN o.status = :processing THEN o.retryCount + 1 ELSE o.retryCount END,
               o.status = :processing,
               o.updatedAt = :now,
               o.version = o.version + 1
         WHERE o.id = :id
           AND o.retryCount < :maxRetries
           AND (o.status IN (:pending, :failed)
                OR (o.status = :processing AND o.updatedAt < :stuckBefore))
        """)
    int claim(@Param("id") UUID id,
              @Param("now") Instant now,
              @Param("stuckBefore") Instant stuckBefore,
              @Param("maxRetries") int maxRetries,
              @Param("pending") NotificationStatus pending,
              @Param("failed") NotificationStatus failed,
              @Param("processing") NotificationStatus processing);

    @Query("""
        SELECT o.id FROM NotificationOutbox o
         WHERE o.retryCount < :maxRetries
           AND ((o.status = :pending AND o.createdAt < :pendingBefore)
                OR o.status = :failed
                OR (o.status = :processing AND o.updatedAt < :stuckBefore))
         ORDER BY o.createdAt ASC
        """)
    List<UUID> findSweepCandidates(@Param("pendingBefore") Instant pendingBefore,
                                   @Param("stuckBefore") Instant stuckBefore,
                                   @Param("maxRetries") int maxRetries,
                                   @Param("pending") NotificationStatus pending,
                                   @Param("failed") NotificationStatus failed,
                                   @Param("processing") NotificationStatus processing,
                                   Limit limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE NotificationOutbox o
           SET o.status = :dlq, o.updatedAt = :now, o.version = o.version + 1
         WHERE o.status = :processing
           AND o.updatedAt < :stuckBefore
           AND o.retryCount >= :maxRetries
        """)
    int markExhaustedStuckAsDlq(@Param("now") Instant now,
                                @Param("stuckBefore") Instant stuckBefore,
                                @Param("maxRetries") int maxRetries,
                                @Param("processing") NotificationStatus processing,
                                @Param("dlq") NotificationStatus dlq);
}
