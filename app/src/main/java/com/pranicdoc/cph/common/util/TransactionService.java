package com.pranicdoc.cph.common.util;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs an action only once the caller's enclosing transaction has committed — for anything
 * that reads back a row the transaction just wrote on its own thread/connection (an
 * {@code @Async} handoff, a Kafka publish) where firing before commit means the row isn't
 * visible there yet under READ COMMITTED. See {@code ProtocolImportJobService#submitImport}
 * for the bug this fixes: the async runner queried a just-saved job row before the save had
 * committed and bailed out as "vanished".
 *
 * <p>Requires an active transaction — {@link TransactionSynchronizationManager#registerSynchronization}
 * throws {@link IllegalStateException} otherwise, so callers must be {@code @Transactional}.
 * A plain Mockito unit test (no real transaction) needs {@code
 * TransactionSynchronizationManager.initSynchronization()}/{@code clearSynchronization()}
 * around it and must fire the registered callback manually, since nothing is actually
 * committing — see {@code ProtocolImportJobServiceTest} for the pattern.
 */
@Component
public class TransactionService {

    public void runAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * Like {@link #runAfterCommit}, but also usable with no transaction in play: inside an active
     * transaction synchronization the action runs after commit (and never on rollback); otherwise it
     * runs immediately on the caller's thread. For code that is sometimes called from a
     * {@code @Transactional} method and sometimes not, such as an event publisher.
     */
    public void runAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            runAfterCommit(action);
        } else {
            action.run();
        }
    }
}
