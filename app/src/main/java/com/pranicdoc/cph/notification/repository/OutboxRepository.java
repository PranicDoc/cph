package com.pranicdoc.cph.notification.repository;

import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query(value = """
        SELECT * FROM notification_outbox
        WHERE status = 'PENDING'
           OR (status = 'FAILED' AND retry_count < 3 AND updated_at < NOW() - INTERVAL '5 Minutes')
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<NotificationOutbox> findPendingJobsForWorker(@Param("batchSize") int batchSize);
}