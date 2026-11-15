package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.notification.channel.ChannelType;
import com.pranicdoc.cph.notification.channel.NotificationChannel;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The after-commit entry points with a real TransactionService and real commit / rollback callbacks. */
class NotificationSenderAfterCommitTest {

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

    private final NotificationChannel email = mock(NotificationChannel.class);
    private final TransactionTemplate tx = new TransactionTemplate(new InMemoryTxManager());
    private final NotificationMessage mail = NotificationMessage.email("asha@example.com", "Hi", "Body");

    private NotificationSender senderOn(Executor executor) {
        when(email.type()).thenReturn(ChannelType.EMAIL);
        return new NotificationSender(List.of(email), new TransactionService(), executor);
    }

    private static final Executor FULL = r -> {
        throw new TaskRejectedException("queue full");
    };

    @Test
    void messageOverload_deliversOnlyAfterCommit() {
        NotificationSender sender = senderOn(Runnable::run);

        tx.executeWithoutResult(status -> {
            sender.sendAfterCommit(mail);
            verify(email, never()).send(any());
        });

        verify(email).send(mail);
    }

    @Test
    void supplierOverload_isNotEvaluatedBeforeCommit_andIsEvaluatedAfter() {
        NotificationSender sender = senderOn(Runnable::run);
        AtomicInteger evaluated = new AtomicInteger();

        tx.executeWithoutResult(status -> {
            sender.sendAfterCommit(() -> {
                evaluated.incrementAndGet();
                return mail;
            });
            assertThat(evaluated).hasValue(0);
        });

        assertThat(evaluated).hasValue(1);
        verify(email).send(mail);
    }

    @Test
    void nothingIsEvaluatedOrSentOnRollback() {
        NotificationSender sender = senderOn(Runnable::run);
        AtomicInteger evaluated = new AtomicInteger();

        tx.executeWithoutResult(status -> {
            sender.sendAfterCommit(() -> {
                evaluated.incrementAndGet();
                return mail;
            });
            sender.sendAfterCommit(mail);
            status.setRollbackOnly();
        });

        assertThat(evaluated).hasValue(0);
        verify(email, never()).send(any());
    }

    @Test
    void supplierThatThrows_sendsNothingAndDoesNotEscape() {
        NotificationSender sender = senderOn(Runnable::run);

        assertThatCode(() -> tx.executeWithoutResult(status -> sender.sendAfterCommit(() -> {
            throw new IllegalStateException("db down");
        }))).doesNotThrowAnyException();

        verify(email, never()).send(any());
    }

    @Test
    void supplierThatReturnsNull_sendsNothing() {
        NotificationSender sender = senderOn(Runnable::run);

        tx.executeWithoutResult(status -> sender.sendAfterCommit(() -> null));

        verify(email, never()).send(any());
    }

    @Test
    void aFullExecutor_neverFailsTheCommittedCaller_onEitherOverload() {
        NotificationSender sender = senderOn(FULL);

        assertThatCode(() -> tx.executeWithoutResult(status -> {
            sender.sendAfterCommit(mail);
            sender.sendAfterCommit(() -> mail);
        })).doesNotThrowAnyException();

        verify(email, never()).send(any());
    }

    @Test
    void aRejectedExecutor_doesNotSkipLaterAfterCommitCallbacks() {
        NotificationSender sender = senderOn(r -> {
            throw new RejectedExecutionException("full");
        });
        AtomicInteger laterCallback = new AtomicInteger();
        TransactionService transactions = new TransactionService();

        tx.executeWithoutResult(status -> {
            sender.sendAfterCommit(mail);
            transactions.runAfterCommit(laterCallback::incrementAndGet);
        });

        assertThat(laterCallback).hasValue(1);
    }

    @Test
    void withNoTransaction_deliversOnTheExecutorAndDoesNotThrow() {
        AtomicInteger handedOff = new AtomicInteger();
        NotificationSender sender = senderOn(r -> {
            handedOff.incrementAndGet();
            r.run();
        });

        assertThatCode(() -> {
            sender.sendAfterCommit(mail);
            sender.sendAfterCommit(() -> mail);
        }).doesNotThrowAnyException();

        assertThat(handedOff).hasValue(2);
        verify(email, org.mockito.Mockito.times(2)).send(mail);
    }

    @Test
    void withNoTransactionAndAFullExecutor_doesNotThrow() {
        NotificationSender sender = senderOn(FULL);

        assertThatCode(() -> {
            sender.sendAfterCommit(mail);
            sender.sendAfterCommit(() -> mail);
        }).doesNotThrowAnyException();
    }
}
