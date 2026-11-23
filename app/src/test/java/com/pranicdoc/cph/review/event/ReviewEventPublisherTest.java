package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.common.feature.FeatureFlags;
import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.review.stream.EventStreamRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ReviewEventPublisherTest {

    /** Runs real transaction synchronization (commit / rollback callbacks) without a database. */
    static class InMemoryTxManager extends AbstractPlatformTransactionManager {
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

    private static FeatureFlags flags() {
        return flags(true);
    }

    private static FeatureFlags flags(boolean on) {
        FeatureFlags flags = mock(FeatureFlags.class);
        org.mockito.Mockito.when(flags.caseReviewEnabledFor(any())).thenReturn(on);
        return flags;
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventStreamRegistry registry = mock(EventStreamRegistry.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final TransactionTemplate tx = new TransactionTemplate(new InMemoryTxManager());

    private final ReviewEvent event = new ReviewEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            ReviewEventType.REVISION_ADDED, 2);

    @Test
    void publish_sendsAfterCommitOnly() {
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class), new TransactionService(), flags());

        tx.executeWithoutResult(status -> {
            publisher.publish(event);
            verifyNoInteractions(redis);
            status.setRollbackOnly();
        });
        verifyNoInteractions(redis);

        tx.executeWithoutResult(status -> {
            publisher.publish(event);
            verifyNoInteractions(redis);
        });
        verify(redis).convertAndSend(eq(ReviewEventPublisher.CHANNEL), anyString());
        verifyNoInteractions(registry);
    }

    @Test
    void publish_handsTheEventToTheNotifierInTheTransaction_butSendsTheLiveEventOnlyAfterCommit() {
        var notifier = mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class);
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, (StringRedisTemplate) null, false,
                notifier, new TransactionService(), flags());

        tx.executeWithoutResult(status -> {
            publisher.publish(event);
            verify(notifier).onEvent(event);
            verify(registry, never()).deliver(any());
        });

        verify(registry).deliver(event);
    }

    private ReviewEventPublisher publisherWithRealMail(com.pranicdoc.cph.auth.repository.HealerRepository healers,
                                                       java.util.concurrent.Executor mailExecutor) {
        var transactions = new TransactionService();
        var channel = mock(com.pranicdoc.cph.notification.channel.NotificationChannel.class);
        org.mockito.Mockito.when(channel.type()).thenReturn(com.pranicdoc.cph.notification.channel.ChannelType.EMAIL);
        var sender = new com.pranicdoc.cph.notification.service.NotificationSender(
                java.util.List.of(channel),
                transactions, mailExecutor);
        var notifier = new com.pranicdoc.cph.review.notify.ReviewNotifier(healers,
                new com.pranicdoc.cph.review.notify.ReviewEmails(new com.pranicdoc.cph.notification.email.EmailTemplates()),
                sender, "https://app.example.test/accept-invite");
        return new ReviewEventPublisher(registry, mapper, (StringRedisTemplate) null, false, notifier, transactions, flags());
    }

    @Test
    void publish_withMailLookupsThatWouldThrow_readsNothingInTheTransactionAndStillCommits() {
        var healers = mock(com.pranicdoc.cph.auth.repository.HealerRepository.class);
        org.mockito.Mockito.when(healers.findById(any())).thenThrow(new IllegalStateException("db down"));
        ReviewEventPublisher publisher = publisherWithRealMail(healers, Runnable::run);

        // A commit that had been marked rollback-only would throw UnexpectedRollbackException here.
        tx.executeWithoutResult(status -> {
            publisher.publish(event);
            verifyNoInteractions(healers);
            assertThat(status.isRollbackOnly()).isFalse();
        });

        verify(registry).deliver(event);
    }

    @Test
    void publish_whenTheMailExecutorIsFull_stillDeliversTheLiveEvent() {
        var healers = mock(com.pranicdoc.cph.auth.repository.HealerRepository.class);
        ReviewEventPublisher publisher = publisherWithRealMail(healers, r -> {
            throw new org.springframework.core.task.TaskRejectedException("queue full");
        });

        tx.executeWithoutResult(status -> publisher.publish(event));

        verify(registry).deliver(event);
    }

    @Test
    void publish_sendsTheFullEventAsJsonOnTheReviewChannel() throws Exception {
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class), new TransactionService(), flags());
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);

        publisher.publish(event);

        verify(redis).convertAndSend(eq("cph:review-events"), captor.capture());
        assertThat(mapper.readValue(captor.getValue(), ReviewEvent.class)).isEqualTo(event);
    }

    @Test
    void publish_withRedisOff_deliversInProcessAfterCommit() {
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, (StringRedisTemplate) null, false, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class), new TransactionService(), flags());

        tx.executeWithoutResult(status -> {
            publisher.publish(event);
            verify(registry, never()).deliver(any());
        });

        verify(registry).deliver(event);
    }

    @Test
    void publish_whenRedisFails_fallsBackToLocalDelivery() {
        doThrow(new RedisConnectionFailureException("down")).when(redis).convertAndSend(anyString(), anyString());
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class), new TransactionService(), flags());

        publisher.publish(event);

        verify(registry).deliver(event);
    }

    @Test
    void relay_deliversTheDecodedEvent() throws Exception {
        ReviewRedisRelay relay = new ReviewRedisRelay(registry, mapper);
        byte[] body = mapper.writeValueAsBytes(event);

        relay.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                ReviewEventPublisher.CHANNEL.getBytes(), body), null);

        verify(registry).deliver(event);
    }

    @Test
    void relay_survivesAnEventWithNoReviewer() throws Exception {
        ReviewRedisRelay relay = new ReviewRedisRelay(registry, mapper);
        ReviewEvent noReviewer = new ReviewEvent(event.submissionId(), event.healerId(), null,
                ReviewEventType.STATUS_CHANGED, 1);

        relay.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                ReviewEventPublisher.CHANNEL.getBytes(), mapper.writeValueAsBytes(noReviewer)), null);

        verify(registry).deliver(noReviewer);
    }

    @Test
    void relay_ignoresMalformedMessages() {
        ReviewRedisRelay relay = new ReviewRedisRelay(registry, mapper);

        relay.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                ReviewEventPublisher.CHANNEL.getBytes(), "not json".getBytes()), null);

        verifyNoInteractions(registry);
    }

    @Test
    void relayRoundTripsBothEventKinds() throws Exception {
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class), new TransactionService(), flags());
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        PdfReadyEvent pdf = new PdfReadyEvent(UUID.randomUUID(), UUID.randomUUID(), "job-1");

        publisher.publish(event);
        publisher.publishPdfReady(pdf);

        verify(redis, org.mockito.Mockito.times(2)).convertAndSend(eq(ReviewEventPublisher.CHANNEL), captor.capture());
        ReviewRedisRelay relay = new ReviewRedisRelay(registry, mapper);
        for (String json : captor.getAllValues()) {
            relay.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                    ReviewEventPublisher.CHANNEL.getBytes(), json.getBytes()), null);
        }
        verify(registry).deliver(event);
        verify(registry).deliver(pdf);
    }

    @Test
    void relay_stillReadsAReviewEventSentBeforeTheKindFieldExisted() throws Exception {
        ReviewRedisRelay relay = new ReviewRedisRelay(registry, mapper);
        String legacy = "{\"submissionId\":\"" + event.submissionId() + "\",\"healerId\":\"" + event.healerId()
                + "\",\"reviewerId\":\"" + event.reviewerId() + "\",\"type\":\"REVISION_ADDED\","
                + "\"revisionNo\":2,\"actor\":null}";

        relay.onMessage(new org.springframework.data.redis.connection.DefaultMessage(
                ReviewEventPublisher.CHANNEL.getBytes(), legacy.getBytes()), null);

        verify(registry).deliver(event);
    }

    @Test
    void sendsNothingWhenOff() {
        var notifier = mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class);
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, notifier,
                new TransactionService(), flags(false));

        publisher.publish(event);
        publisher.publishPdfReady(new PdfReadyEvent(UUID.randomUUID(), UUID.randomUUID(), "job-1"));

        verifyNoInteractions(notifier, registry, redis);
    }

    @Test
    void eventForAnEnabledHealerIsSent() {
        var notifier = mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class);
        FeatureFlags perHealer = mock(FeatureFlags.class);
        org.mockito.Mockito.when(perHealer.caseReviewEnabledFor(event.healerId())).thenReturn(true);
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, notifier,
                new TransactionService(), perHealer);
        PdfReadyEvent pdf = new PdfReadyEvent(event.healerId(), UUID.randomUUID(), "job-1");

        publisher.publish(event);
        publisher.publishPdfReady(pdf);

        verify(notifier).onEvent(event);
        verify(redis, org.mockito.Mockito.times(2)).convertAndSend(eq(ReviewEventPublisher.CHANNEL), anyString());
    }

    @Test
    void eventForAnotherHealerIsSuppressed() {
        var notifier = mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class);
        FeatureFlags perHealer = mock(FeatureFlags.class);
        UUID other = UUID.randomUUID();
        org.mockito.Mockito.when(perHealer.caseReviewEnabledFor(other)).thenReturn(true);
        org.mockito.Mockito.when(perHealer.caseReviewEnabledFor(event.healerId())).thenReturn(false);
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, notifier,
                new TransactionService(), perHealer);

        publisher.publish(event);
        publisher.publishPdfReady(new PdfReadyEvent(event.healerId(), UUID.randomUUID(), "job-1"));

        verifyNoInteractions(notifier, registry, redis);
    }
}
