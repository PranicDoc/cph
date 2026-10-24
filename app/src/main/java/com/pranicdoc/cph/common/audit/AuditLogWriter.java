package com.pranicdoc.cph.common.audit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes audit_log rows off the request thread.
 *
 * A request that has touched the database keeps its pooled connection until it ends (its own
 * transaction, or the open-in-view EntityManager). Writing the audit row in a REQUIRES_NEW
 * transaction from that thread needed a second connection from the same pool, so with as many
 * concurrent requests as connections every request held one and waited for another until the pool
 * timed out. Here the request thread only queues the row; one writer thread saves queued rows in
 * batches and never holds a connection while waiting for another.
 *
 * Rows are written moments after the request, and rows still queued when the process dies are lost.
 * The queue is bounded so a database outage cannot exhaust memory: when it is full the row is
 * dropped and logged rather than blocking the request.
 */
@Component
@Slf4j
public class AuditLogWriter {

    private static final int QUEUE_CAPACITY = 10_000;
    private static final int BATCH_SIZE = 200;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final AuditLogRepository auditLogRepository;
    private final AuditorAware<String> auditorAware;
    private final TransactionTemplate transactionTemplate;
    private final BlockingQueue<AuditLog> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    /** Queued plus being written; zero means everything handed in so far has been dealt with. */
    private final AtomicInteger pending = new AtomicInteger();
    private final Thread worker = new Thread(this::run, "audit-log-writer");
    private volatile boolean running = true;

    public AuditLogWriter(AuditLogRepository auditLogRepository,
                          AuditorAware<String> auditorAware,
                          PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.auditorAware = auditorAware;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.worker.setDaemon(true);
    }

    @PostConstruct
    void start() {
        worker.start();
    }

    /**
     * Queues the row; never blocks. created_by is resolved here because only the request thread
     * knows who is signed in.
     */
    public void enqueue(AuditLog auditLog) {
        try {
            auditorAware.getCurrentAuditor().ifPresent(auditor -> {
                auditLog.setCreatedBy(auditor);
                auditLog.setUpdatedBy(auditor);
            });
        } catch (RuntimeException ex) {
            log.warn("Could not resolve created_by for audit action {}", auditLog.getAction(), ex);
        }
        pending.incrementAndGet();
        if (!queue.offer(auditLog)) {
            pending.decrementAndGet();
            log.error("Audit queue full ({} rows), dropped audit row for action {}", QUEUE_CAPACITY, auditLog.getAction());
        }
    }

    /** Waits until every row queued so far has been written (or dropped); for tests. */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (pending.get() > 0) {
            if (System.nanoTime() > deadline) return false;
            Thread.sleep(10);
        }
        return true;
    }

    private void run() {
        List<AuditLog> batch = new ArrayList<>(BATCH_SIZE);
        while (running) {
            try {
                AuditLog first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);
                write(batch);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                pending.addAndGet(-batch.size());
                batch.clear();
            }
        }
    }

    private void write(List<AuditLog> batch) throws InterruptedException {
        try {
            save(batch);
        } catch (RuntimeException first) {
            log.warn("Audit write of {} rows failed, retrying once", batch.size(), first);
            Thread.sleep(RETRY_DELAY.toMillis());
            try {
                save(batch);
            } catch (RuntimeException second) {
                log.error("Dropped {} audit rows: write failed twice", batch.size(), second);
            }
        }
    }

    private void save(List<AuditLog> batch) {
        transactionTemplate.executeWithoutResult(status -> auditLogRepository.saveAll(batch));
    }

    /** Stops the writer and saves whatever is still queued before the datasource goes away. */
    @PreDestroy
    void stop() throws InterruptedException {
        running = false;
        // The worker notices within one poll (1s) and finishes the batch it is writing.
        worker.join(TimeUnit.SECONDS.toMillis(5));
        if (worker.isAlive()) {
            worker.interrupt();
        }
        List<AuditLog> rest = new ArrayList<>();
        queue.drainTo(rest);
        for (int from = 0; from < rest.size(); from += BATCH_SIZE) {
            List<AuditLog> batch = rest.subList(from, Math.min(from + BATCH_SIZE, rest.size()));
            try {
                save(batch);
            } catch (RuntimeException ex) {
                log.error("Dropped {} audit rows at shutdown: write failed", batch.size(), ex);
            }
        }
        pending.addAndGet(-rest.size());
    }
}
