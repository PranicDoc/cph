package com.pranicdoc.cph.review.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.notification.channel.ChannelType;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.email.EmailTemplates;
import com.pranicdoc.cph.notification.service.NotificationSender;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.stream.EventStreamRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who is emailed for each review event, decided from the event alone, and that a mail problem never
 * reaches the review action. The mail itself is built later, by the supplier handed to the sender.
 */
class ReviewNotifierTest {

    private static final String BASE = "https://app.example.test";

    private final HealerRepository healers = mock(HealerRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);

    private final UUID healerId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();

    private ReviewNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new ReviewNotifier(healers, new ReviewEmails(new EmailTemplates()), sender,
                BASE + "/accept-invite");
        when(healers.findById(healerId)).thenReturn(Optional.of(healer("Asha Rao", "asha@example.com")));
        when(healers.findById(reviewerId)).thenReturn(Optional.of(healer("Ravi Menon", "ravi@example.com")));
    }

    private static Healer healer(String name, String email) {
        return Healer.builder().fullName(name).email(email).build();
    }

    @SuppressWarnings("unchecked")
    private Supplier<NotificationMessage> queued() {
        ArgumentCaptor<Supplier<NotificationMessage>> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(sender).sendAfterCommit(captor.capture());
        return captor.getValue();
    }

    /** What the queued supplier builds once the commit has happened. */
    private NotificationMessage built() {
        return queued().get();
    }

    @SuppressWarnings("unchecked")
    private void assertNothingQueued() {
        verify(sender, never()).sendAfterCommit(any(Supplier.class));
        verify(sender, never()).sendAfterCommit(any(NotificationMessage.class));
    }

    private ReviewEvent event(ReviewEventType type, Integer revisionNo, AuthorSide actor, ReviewStatus status) {
        return new ReviewEvent(submissionId, healerId, reviewerId, type, revisionNo, actor, status);
    }

    @Test
    void submit_emailsReviewer() {
        notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null, ReviewStatus.PENDING_REVIEW));

        NotificationMessage m = built();
        assertThat(m.channel()).isEqualTo(ChannelType.EMAIL);
        assertThat(m.recipient()).isEqualTo("ravi@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: A case was submitted for your review");
        assertThat(m.body()).contains("Asha Rao submitted a case").contains(BASE + "/reviews/" + submissionId);
        assertThat(m.htmlBody()).contains("href=\"" + BASE + "/reviews/" + submissionId + "\"");
    }

    @Test
    void resubmit_emailsReviewerWithResubmittedSubject() {
        notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 2, null, ReviewStatus.PENDING_REVIEW));

        NotificationMessage m = built();
        assertThat(m.recipient()).isEqualTo("ravi@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: A case was resubmitted for your review");
        assertThat(m.body()).contains("version 2");
    }

    @Test
    void approve_emailsHealer() {
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.APPROVED));

        NotificationMessage m = built();
        assertThat(m.recipient()).isEqualTo("asha@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: Your case was approved");
        assertThat(m.body()).contains(BASE + "/submissions/" + submissionId);
    }

    @Test
    void requestChanges_emailsHealer() {
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.CHANGES_REQUESTED));

        NotificationMessage m = built();
        assertThat(m.recipient()).isEqualTo("asha@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: Changes requested on your case");
    }

    @Test
    void noReviewer_sendsNothingAndLooksNoOneUp() {
        notifier.onEvent(new ReviewEvent(submissionId, healerId, null, ReviewEventType.REVISION_ADDED, 1, null,
                ReviewStatus.PENDING_REVIEW));
        notifier.onEvent(new ReviewEvent(submissionId, healerId, null, ReviewEventType.STATUS_CHANGED, 1, null,
                ReviewStatus.APPROVED));
        notifier.onEvent(new ReviewEvent(submissionId, healerId, null, ReviewEventType.COMMENT_ADDED, 1,
                AuthorSide.HEALER, ReviewStatus.PENDING_REVIEW));

        assertNothingQueued();
        verifyNoInteractions(healers);
    }

    @Test
    void reRequest_statusChangedToPending_emailsReviewerLikeAResubmit() {
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 2, null, ReviewStatus.PENDING_REVIEW));

        NotificationMessage m = built();
        assertThat(m.recipient()).isEqualTo("ravi@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: A case was resubmitted for your review");
        assertThat(m.body()).contains("version 2");
    }

    @Test
    void dismissal_sendsNothing() {
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.DISMISSED));

        assertNothingQueued();
    }

    @Test
    void reply_goesToTheOtherParty() {
        notifier.onEvent(event(ReviewEventType.COMMENT_ADDED, 1, AuthorSide.HEALER, ReviewStatus.PENDING_REVIEW));

        NotificationMessage toReviewer = built();
        assertThat(toReviewer.recipient()).isEqualTo("ravi@example.com");
        assertThat(toReviewer.subject()).isEqualTo("HealingARK: New reply on a case review");
        assertThat(toReviewer.body()).contains("Asha Rao").contains(BASE + "/reviews/" + submissionId);
    }

    @Test
    void reviewerComment_goesToTheHealer() {
        notifier.onEvent(event(ReviewEventType.COMMENT_ADDED, 1, AuthorSide.REVIEWER, ReviewStatus.PENDING_REVIEW));

        NotificationMessage m = built();
        assertThat(m.recipient()).isEqualTo("asha@example.com");
        assertThat(m.body()).contains("Ravi Menon").contains(BASE + "/submissions/" + submissionId);
    }

    @Test
    void commentWithNoAuthorSide_sendsNothing() {
        notifier.onEvent(event(ReviewEventType.COMMENT_ADDED, 1, null, ReviewStatus.PENDING_REVIEW));

        assertNothingQueued();
    }

    @Test
    void onEvent_readsNothing_theLookupsWaitForTheQueuedSupplier() {
        notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null, ReviewStatus.PENDING_REVIEW));
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.APPROVED));
        notifier.onEvent(event(ReviewEventType.COMMENT_ADDED, 1, AuthorSide.HEALER, ReviewStatus.PENDING_REVIEW));

        verifyNoInteractions(healers);
    }

    @Test
    void theKindOfMailIsDecidedAtPublishTime_notReReadAfterwards() {
        // Approved at publish time; the approval is dismissed before the mail is built.
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.APPROVED));
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.DISMISSED));

        NotificationMessage m = built();
        assertThat(m.subject()).isEqualTo("HealingARK: Your case was approved");
    }

    @Test
    void aMissingHealerBuildsNoMessage() {
        when(healers.findById(healerId)).thenReturn(Optional.empty());
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null, ReviewStatus.APPROVED));

        assertThat(built()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSenderFailure_doesNotReachTheCaller() {
        doThrow(new IllegalStateException("boom")).when(sender).sendAfterCommit(any(Supplier.class));

        assertThatCode(() -> notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null,
                ReviewStatus.PENDING_REVIEW))).doesNotThrowAnyException();
    }

    @Test
    void publisher_survivesNotifierFailure() {
        ReviewNotifier failing = mock(ReviewNotifier.class);
        doThrow(new IllegalStateException("boom")).when(failing).onEvent(any());
        ReviewEventPublisher publisher = new ReviewEventPublisher(mock(EventStreamRegistry.class),
                new ObjectMapper(), null, false, failing, new TransactionService());

        assertThatCode(() -> publisher.publish(event(ReviewEventType.REVISION_ADDED, 1, null,
                ReviewStatus.PENDING_REVIEW))).doesNotThrowAnyException();

        verify(failing).onEvent(any());
    }
}
