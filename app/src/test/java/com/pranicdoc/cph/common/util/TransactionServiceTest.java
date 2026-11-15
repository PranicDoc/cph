package com.pranicdoc.cph.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionServiceTest {

    /** Runs real transaction synchronization (commit / rollback callbacks) without a database. */
    private static class InMemoryTxManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private final TransactionService service = new TransactionService();
    private final TransactionTemplate tx = new TransactionTemplate(new InMemoryTxManager());
    private final AtomicInteger runs = new AtomicInteger();

    @Test
    void runAfterCommit_runsOnlyOnceTheTransactionCommitted() {
        tx.executeWithoutResult(status -> {
            service.runAfterCommit(runs::incrementAndGet);
            assertThat(runs).hasValue(0);
        });

        assertThat(runs).hasValue(1);
    }

    @Test
    void runAfterCommit_withoutATransaction_isRefused() {
        assertThatThrownBy(() -> service.runAfterCommit(runs::incrementAndGet))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runAfterCommitOrNow_insideATransaction_waitsForTheCommit() {
        tx.executeWithoutResult(status -> {
            service.runAfterCommitOrNow(runs::incrementAndGet);
            assertThat(runs).hasValue(0);
        });

        assertThat(runs).hasValue(1);
    }

    @Test
    void runAfterCommitOrNow_insideATransaction_neverRunsOnRollback() {
        tx.executeWithoutResult(status -> {
            service.runAfterCommitOrNow(runs::incrementAndGet);
            status.setRollbackOnly();
        });

        assertThat(runs).hasValue(0);
    }

    @Test
    void runAfterCommitOrNow_withoutATransaction_runsAtOnce() {
        service.runAfterCommitOrNow(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }
}
