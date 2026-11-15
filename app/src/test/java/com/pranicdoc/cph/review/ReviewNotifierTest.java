package com.pranicdoc.cph.review;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.notification.channel.ChannelType;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.email.EmailTemplates;
import com.pranicdoc.cph.notification.service.NotificationSender;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.event.ReviewEventType;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewStage;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.model.SubmittedVia;
import com.pranicdoc.cph.review.notify.ReviewEmails;
import com.pranicdoc.cph.review.notify.ReviewNotifier;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import com.pranicdoc.cph.review.stream.ReviewStreamRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Who is emailed for each review event, and that a mail problem never reaches the review action. */
class ReviewNotifierTest {

    private static final String BASE = "https://app.example.test";

    private final ReviewSubmissionRepository submissions = mock(ReviewSubmissionRepository.class);
    private final HealerRepository healers = mock(HealerRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final Executor sameThread = Runnable::run;

    private final UUID healerId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();

    private ReviewNotifier notifier;
    private ReviewSubmission submission;

    @BeforeEach
    void setUp() {
        notifier = new ReviewNotifier(submissions, healers, new ReviewEmails(new EmailTemplates()), sender,
                sameThread, BASE + "/accept-invite");
        submission = ReviewSubmission.builder().caseId(UUID.randomUUID()).healerId(healerId)
                .reviewerId(reviewerId).stage(ReviewStage.REVIEWER).status(ReviewStatus.PENDING_REVIEW)
                .submittedVia(SubmittedVia.MANUAL).build();
        submission.setId(submissionId);
        when(submissions.findById(submissionId)).thenReturn(Optional.of(submission));
        when(healers.findById(healerId)).thenReturn(Optional.of(healer("Asha Rao", "asha@example.com")));
        when(healers.findById(reviewerId)).thenReturn(Optional.of(healer("Ravi Menon", "ravi@example.com")));
    }

    private static Healer healer(String name, String email) {
        return Healer.builder().fullName(name).email(email).build();
    }

    private NotificationMessage sent() {
        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(sender).sendNow(captor.capture());
        return captor.getValue();
    }

    private ReviewEvent event(ReviewEventType type, Integer revisionNo, AuthorSide actor) {
        return new ReviewEvent(submissionId, healerId, reviewerId, type, revisionNo, actor);
    }

    @Test
    void submit_emailsReviewer() {
        notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null));

        NotificationMessage m = sent();
        assertThat(m.channel()).isEqualTo(ChannelType.EMAIL);
        assertThat(m.recipient()).isEqualTo("ravi@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: A case was submitted for your review");
        assertThat(m.body()).contains("Asha Rao submitted a case").contains(BASE + "/reviews/" + submissionId);
        assertThat(m.htmlBody()).contains("href=\"" + BASE + "/reviews/" + submissionId + "\"");
    }

    @Test
    void resubmit_emailsReviewerWithResubmittedSubject() {
        notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 2, null));

        NotificationMessage m = sent();
        assertThat(m.recipient()).isEqualTo("ravi@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: A case was resubmitted for your review");
        assertThat(m.body()).contains("version 2");
    }

    @Test
    void approve_emailsHealer() {
        submission.setStatus(ReviewStatus.APPROVED);

        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null));

        NotificationMessage m = sent();
        assertThat(m.recipient()).isEqualTo("asha@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: Your case was approved");
        assertThat(m.body()).contains(BASE + "/submissions/" + submissionId);
    }

    @Test
    void requestChanges_emailsHealer() {
        submission.setStatus(ReviewStatus.CHANGES_REQUESTED);

        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null));

        NotificationMessage m = sent();
        assertThat(m.recipient()).isEqualTo("asha@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: Changes requested on your case");
    }

    @Test
    void noReviewer_sendsNothingAndLooksNoOneUpByNull() {
        submission.setReviewerId(null);

        notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null));
        submission.setStatus(ReviewStatus.APPROVED);
        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null));
        notifier.onEvent(event(ReviewEventType.COMMENT_ADDED, 1, AuthorSide.HEALER));

        verify(sender, never()).sendNow(any());
        verify(healers, never()).findById(null);
    }

    @Test
    void reRequest_statusChangedToPending_emailsReviewerLikeAResubmit() {
        submission.setStatus(ReviewStatus.PENDING_REVIEW);

        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 2, null));

        NotificationMessage m = sent();
        assertThat(m.recipient()).isEqualTo("ravi@example.com");
        assertThat(m.subject()).isEqualTo("HealingARK: A case was resubmitted for your review");
        assertThat(m.body()).contains("version 2");
    }

    @Test
    void dismissal_sendsNothing() {
        submission.setStatus(ReviewStatus.DISMISSED);

        notifier.onEvent(event(ReviewEventType.STATUS_CHANGED, 1, null));

        verify(sender, never()).sendNow(any());
    }

    @Test
    void reply_goesToTheOtherParty() {
        notifier.onEvent(event(ReviewEventType.COMMENT_ADDED, 1, AuthorSide.HEALER));
        NotificationMessage toReviewer = sent();
        assertThat(toReviewer.recipient()).isEqualTo("ravi@example.com");
        assertThat(toReviewer.subject()).isEqualTo("HealingARK: New reply on a case review");
        assertThat(toReviewer.body()).contains("Asha Rao").contains(BASE + "/reviews/" + submissionId);
    }

    @Test
    void reviewerComment_goesToTheHealer() {
        notifier.onEvent(event(ReviewEventType.COMMENT_ADDED, 1, AuthorSide.REVIEWER));

        NotificationMessage m = sent();
        assertThat(m.recipient()).isEqualTo("asha@example.com");
        assertThat(m.body()).contains("Ravi Menon").contains(BASE + "/submissions/" + submissionId);
    }

    @Test
    void mailFailure_doesNotRollBack() {
        doThrow(new IllegalStateException("smtp down")).when(sender).sendNow(any());
        when(submissions.findById(submissionId)).thenReturn(Optional.of(submission));

        assertThatCode(() -> notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null)))
                .doesNotThrowAnyException();

        // A broken repository and a rejecting executor are swallowed the same way.
        when(submissions.findById(submissionId)).thenThrow(new IllegalStateException("db down"));
        assertThatCode(() -> notifier.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null)))
                .doesNotThrowAnyException();
        ReviewNotifier rejecting = new ReviewNotifier(submissions, healers, new ReviewEmails(new EmailTemplates()),
                sender, r -> {
                    throw new java.util.concurrent.RejectedExecutionException("full");
                }, BASE + "/accept-invite");
        assertThatCode(() -> rejecting.onEvent(event(ReviewEventType.REVISION_ADDED, 1, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void publisher_mailsOnceAfterCommitAndSurvivesNotifierFailure() {
        ReviewNotifier failing = mock(ReviewNotifier.class);
        doThrow(new IllegalStateException("boom")).when(failing).onEvent(any());
        ReviewEventPublisher publisher = new ReviewEventPublisher(mock(ReviewStreamRegistry.class),
                new ObjectMapper(), null, false, failing);

        assertThatCode(() -> publisher.publish(event(ReviewEventType.REVISION_ADDED, 1, null)))
                .doesNotThrowAnyException();

        verify(failing).onEvent(any());
    }
}
