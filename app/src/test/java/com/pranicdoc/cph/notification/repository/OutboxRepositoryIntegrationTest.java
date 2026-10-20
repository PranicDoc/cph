package com.pranicdoc.cph.notification.repository;

import com.pranicdoc.cph.common.security.SecurityContextUtil;
import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.entity.NotificationOutbox.NotificationStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OutboxRepositoryIntegrationTest {

    private static final int MAX_RETRIES = 3;
    private static final Duration STUCK_AFTER = Duration.ofMinutes(10);
    private static final Duration PENDING_GRACE = Duration.ofMinutes(2);

    @Autowired private OutboxRepository repository;
    @Autowired private EntityManager entityManager;
    // Required by an entity listener in the JPA slice (same as the masterdata @DataJpaTest classes).
    @MockBean private SecurityContextUtil securityContextUtil;

    @Test
    void claim_movesPendingRowToProcessing_once() {
        UUID id = persist(NotificationStatus.PENDING, 0, Instant.now());
        Instant now = Instant.now();

        int first = repository.claim(id, now, now.minus(STUCK_AFTER), MAX_RETRIES);
        int second = repository.claim(id, now, now.minus(STUCK_AFTER), MAX_RETRIES);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        NotificationOutbox row = repository.findById(id).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(row.getRetryCount()).isZero();
    }

    @Test
    void claim_movesFailedRowWithRetriesLeft() {
        UUID id = persist(NotificationStatus.FAILED, 1, Instant.now());
        Instant now = Instant.now();

        assertThat(repository.claim(id, now, now.minus(STUCK_AFTER), MAX_RETRIES)).isEqualTo(1);
        assertThat(repository.findById(id).orElseThrow().getRetryCount()).isEqualTo(1);
    }

    @Test
    void claim_reclaimsStaleProcessingRow_andCountsItAsAnAttempt() {
        Instant now = Instant.now();
        UUID id = persist(NotificationStatus.PROCESSING, 0, now.minus(Duration.ofMinutes(30)));

        assertThat(repository.claim(id, now, now.minus(STUCK_AFTER), MAX_RETRIES)).isEqualTo(1);
        NotificationOutbox row = repository.findById(id).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        assertThat(row.getRetryCount()).isEqualTo(1);
    }

    @Test
    void claim_skipsFreshProcessingProcessedAndDlqRows() {
        Instant now = Instant.now();
        UUID inFlight = persist(NotificationStatus.PROCESSING, 0, now);
        UUID done = persist(NotificationStatus.PROCESSED, 0, now);
        UUID dead = persist(NotificationStatus.DLQ, 3, now);

        for (UUID id : List.of(inFlight, done, dead)) {
            assertThat(repository.claim(id, now, now.minus(STUCK_AFTER), MAX_RETRIES)).isZero();
        }
    }

    @Test
    void findSweepCandidates_returnsOnlyRowsThatNeedWork() {
        Instant now = Instant.now();
        Instant old = now.minus(Duration.ofMinutes(30));
        UUID oldPending = persist(NotificationStatus.PENDING, 0, old);
        persist(NotificationStatus.PENDING, 0, now);                 // eager path still has it
        UUID failed = persist(NotificationStatus.FAILED, 2, now);
        UUID stuck = persist(NotificationStatus.PROCESSING, 1, old);
        persist(NotificationStatus.PROCESSING, 3, old);              // exhausted -> DLQ, not resent
        persist(NotificationStatus.PROCESSING, 0, now);              // in flight
        persist(NotificationStatus.PROCESSED, 0, old);
        persist(NotificationStatus.DLQ, 3, old);

        List<UUID> ids = repository.findSweepCandidates(
                now.minus(PENDING_GRACE), now.minus(STUCK_AFTER), MAX_RETRIES, 100);

        assertThat(ids).containsExactlyInAnyOrder(oldPending, failed, stuck);
    }

    @Test
    void markExhaustedStuckAsDlq_onlyTouchesStaleProcessingRowsOutOfRetries() {
        Instant now = Instant.now();
        Instant old = now.minus(Duration.ofMinutes(30));
        UUID exhausted = persist(NotificationStatus.PROCESSING, 3, old);
        UUID retryable = persist(NotificationStatus.PROCESSING, 1, old);

        int moved = repository.markExhaustedStuckAsDlq(now, now.minus(STUCK_AFTER), MAX_RETRIES);

        assertThat(moved).isEqualTo(1);
        assertThat(repository.findById(exhausted).orElseThrow().getStatus()).isEqualTo(NotificationStatus.DLQ);
        assertThat(repository.findById(retryable).orElseThrow().getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    /** Persists a row, then backdates created_at/updated_at (which @PrePersist always sets to now). */
    private UUID persist(NotificationStatus status, int retryCount, Instant at) {
        NotificationOutbox row = new NotificationOutbox("+919999999999", "hello");
        row.setStatus(status);
        row.setRetryCount(retryCount);
        entityManager.persist(row);
        entityManager.flush();
        entityManager.createQuery("UPDATE NotificationOutbox o SET o.createdAt = :at, o.updatedAt = :at WHERE o.id = :id")
                .setParameter("at", at)
                .setParameter("id", row.getId())
                .executeUpdate();
        entityManager.clear();
        return row.getId();
    }
}
