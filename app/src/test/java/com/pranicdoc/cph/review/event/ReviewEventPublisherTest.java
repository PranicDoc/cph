package com.pranicdoc.cph.review.event;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventStreamRegistry registry = mock(EventStreamRegistry.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final TransactionTemplate tx = new TransactionTemplate(new InMemoryTxManager());

    private final ReviewEvent event = new ReviewEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            ReviewEventType.REVISION_ADDED, 2);

    @Test
    void publish_sendsAfterCommitOnly() {
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class));

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
    void publish_sendsTheFullEventAsJsonOnTheReviewChannel() throws Exception {
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class));
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);

        publisher.publish(event);

        verify(redis).convertAndSend(eq("cph:review-events"), captor.capture());
        assertThat(mapper.readValue(captor.getValue(), ReviewEvent.class)).isEqualTo(event);
    }

    @Test
    void publish_withRedisOff_deliversInProcessAfterCommit() {
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, (StringRedisTemplate) null, false, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class));

        tx.executeWithoutResult(status -> {
            publisher.publish(event);
            verify(registry, never()).deliver(any());
        });

        verify(registry).deliver(event);
    }

    @Test
    void publish_whenRedisFails_fallsBackToLocalDelivery() {
        doThrow(new RedisConnectionFailureException("down")).when(redis).convertAndSend(anyString(), anyString());
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class));

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
        ReviewEventPublisher publisher = new ReviewEventPublisher(registry, mapper, redis, true, mock(com.pranicdoc.cph.review.notify.ReviewNotifier.class));
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
}
