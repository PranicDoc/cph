package com.pranicdoc.cph.review.notify;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.service.NotificationSender;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewStatus;
import com.pranicdoc.cph.review.model.ReviewSubmission;
import com.pranicdoc.cph.review.repository.ReviewSubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Emails the right party when a review changes. {@link com.pranicdoc.cph.review.event.ReviewEventPublisher}
 * calls it once per event, on the instance that made the change and after that change committed, so a
 * rolled-back action sends nothing and a multi-instance deployment sends once.
 *
 * <ul>
 *   <li>submit / resubmit / re-request of review: the instructor</li>
 *   <li>changes requested / approved: the healer</li>
 *   <li>a comment: the other party than its author</li>
 * </ul>
 *
 * The work runs on the notification executor and never throws: a mail outage or a missing row must not
 * fail or undo the review action. A status change that is neither of the two decisions (a dismissal)
 * sends nothing.
 */
@Slf4j
@Component
public class ReviewNotifier {

    private final ReviewSubmissionRepository submissions;
    private final HealerRepository healers;
    private final ReviewEmails emails;
    private final NotificationSender sender;
    private final Executor executor;
    private final String appUrl;

    public ReviewNotifier(ReviewSubmissionRepository submissions, HealerRepository healers, ReviewEmails emails,
                          NotificationSender sender, @Qualifier("notificationExecutor") Executor executor,
                          @Value("${app.invite.url}") String inviteUrl) {
        this.submissions = submissions;
        this.healers = healers;
        this.emails = emails;
        this.sender = sender;
        this.executor = executor;
        URI uri = URI.create(inviteUrl);
        this.appUrl = uri.getScheme() + "://" + uri.getAuthority();
    }

    public void onEvent(ReviewEvent event) {
        try {
            executor.execute(() -> notifySafely(event));
        } catch (RuntimeException e) {
            log.warn("Review email for {} on submission {} not queued: {}",
                    event.type(), event.submissionId(), e.toString());
        }
    }

    private void notifySafely(ReviewEvent event) {
        try {
            build(event).ifPresent(sender::sendNow);
        } catch (RuntimeException e) {
            log.warn("Review email for {} on submission {} not sent: {}",
                    event.type(), event.submissionId(), e.toString());
        }
    }

    private Optional<NotificationMessage> build(ReviewEvent event) {
        Optional<ReviewSubmission> found = submissions.findById(event.submissionId());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ReviewSubmission s = found.get();
        return switch (event.type()) {
            case REVISION_ADDED -> toReviewer(s, event.revisionNo() == null ? 1 : event.revisionNo());
            case STATUS_CHANGED -> s.getStatus() == ReviewStatus.PENDING_REVIEW
                    ? toReviewer(s, event.revisionNo() == null ? 1 : event.revisionNo()) // a re-request
                    : decision(s);
            case COMMENT_ADDED -> reply(s, event.actor());
        };
    }

    private Optional<NotificationMessage> toReviewer(ReviewSubmission s, int revisionNo) {
        if (s.getReviewerId() == null) { // the instructor's account was erased; nobody to tell yet
            return Optional.empty();
        }
        Optional<Healer> reviewer = healers.findById(s.getReviewerId());
        Optional<Healer> healer = healers.findById(s.getHealerId());
        if (reviewer.isEmpty() || healer.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(emails.submitted(reviewer.get().getEmail(), reviewer.get().getFullName(),
                healer.get().getFullName(), revisionNo, appUrl, link("/reviews/", s.getId())));
    }

    private Optional<NotificationMessage> decision(ReviewSubmission s) {
        if (s.getStatus() != ReviewStatus.CHANGES_REQUESTED && s.getStatus() != ReviewStatus.APPROVED) {
            return Optional.empty();
        }
        if (s.getReviewerId() == null) {
            return Optional.empty();
        }
        Optional<Healer> healer = healers.findById(s.getHealerId());
        Optional<Healer> reviewer = healers.findById(s.getReviewerId());
        if (healer.isEmpty() || reviewer.isEmpty()) {
            return Optional.empty();
        }
        String link = link("/submissions/", s.getId());
        return Optional.of(s.getStatus() == ReviewStatus.APPROVED
                ? emails.approved(healer.get().getEmail(), healer.get().getFullName(),
                        reviewer.get().getFullName(), appUrl, link)
                : emails.changesRequested(healer.get().getEmail(), healer.get().getFullName(),
                        reviewer.get().getFullName(), appUrl, link));
    }

    private Optional<NotificationMessage> reply(ReviewSubmission s, AuthorSide actor) {
        if (actor == null) {
            return Optional.empty();
        }
        boolean fromHealer = actor == AuthorSide.HEALER;
        if (s.getReviewerId() == null) {
            return Optional.empty();
        }
        Optional<Healer> to = healers.findById(fromHealer ? s.getReviewerId() : s.getHealerId());
        Optional<Healer> from = healers.findById(fromHealer ? s.getHealerId() : s.getReviewerId());
        if (to.isEmpty() || from.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(emails.reply(to.get().getEmail(), to.get().getFullName(), from.get().getFullName(),
                appUrl, link(fromHealer ? "/reviews/" : "/submissions/", s.getId())));
    }

    private String link(String path, UUID submissionId) {
        return appUrl + path + submissionId;
    }
}
