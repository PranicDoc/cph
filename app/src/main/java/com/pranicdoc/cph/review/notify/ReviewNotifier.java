package com.pranicdoc.cph.review.notify;

import com.pranicdoc.cph.auth.model.Healer;
import com.pranicdoc.cph.auth.repository.HealerRepository;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.service.NotificationSender;
import com.pranicdoc.cph.review.event.ReviewEvent;
import com.pranicdoc.cph.review.model.AuthorSide;
import com.pranicdoc.cph.review.model.ReviewStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Emails the right party when a review changes. {@link com.pranicdoc.cph.review.event.ReviewEventPublisher}
 * calls it once per event, on the instance that made the change, from inside the writing transaction.
 * It reads nothing there: the kind of mail is decided from the event (which carries the submission's
 * status at that moment), and the healer lookups and message build are handed to
 * {@link NotificationSender#sendAfterCommit(java.util.function.Supplier)}, which runs them after commit on
 * the notification executor. So a rolled-back action sends nothing, a multi-instance deployment sends
 * once, and a failing lookup can neither mark the writing transaction rollback-only nor fail the request.
 *
 * <ul>
 *   <li>submit / resubmit / re-request of review: the instructor</li>
 *   <li>changes requested / approved: the healer</li>
 *   <li>a comment: the other party than its author</li>
 * </ul>
 *
 * It never throws: a mail outage or a missing row must not fail or undo the review action. A status
 * change that is neither of the two decisions (a dismissal) sends nothing.
 */
@Slf4j
@Component
public class ReviewNotifier {

    private final HealerRepository healers;
    private final ReviewEmails emails;
    private final NotificationSender sender;
    private final String appUrl;

    // Hand-written: @Value constructor parameter, and appUrl is derived from it.
    public ReviewNotifier(HealerRepository healers, ReviewEmails emails,
                          NotificationSender sender, @Value("${app.invite.url}") String inviteUrl) {
        this.healers = healers;
        this.emails = emails;
        this.sender = sender;
        URI uri = URI.create(inviteUrl);
        this.appUrl = uri.getScheme() + "://" + uri.getAuthority();
    }

    /**
     * Decides from the event alone (no reads) whether and what to mail, and queues the lookups and the
     * message build for after the caller's commit. Never throws.
     */
    public void onEvent(ReviewEvent event) {
        try {
            Supplier<NotificationMessage> mail = mailFor(event);
            if (mail != null) {
                sender.sendAfterCommit(mail);
            }
        } catch (RuntimeException e) {
            log.warn("Review email for {} on submission {} not queued: {}",
                    event.type(), event.submissionId(), e.toString());
        }
    }

    /** Null when this event sends nothing. */
    private Supplier<NotificationMessage> mailFor(ReviewEvent event) {
        if (event.reviewerId() == null) { // the instructor's account was erased; nobody to tell yet
            return null;
        }
        int revisionNo = event.revisionNo() == null ? 1 : event.revisionNo();
        return switch (event.type()) {
            case REVISION_ADDED -> () -> toReviewer(event, revisionNo);
            case STATUS_CHANGED -> {
                ReviewStatus status = event.status();
                if (status == ReviewStatus.PENDING_REVIEW) { // a re-request
                    yield () -> toReviewer(event, revisionNo);
                }
                if (status == ReviewStatus.CHANGES_REQUESTED || status == ReviewStatus.APPROVED) {
                    yield () -> decision(event, status);
                }
                yield null; // a dismissal, or a status we do not mail
            }
            case COMMENT_ADDED -> event.actor() == null ? null : () -> reply(event, event.actor());
        };
    }

    private NotificationMessage toReviewer(ReviewEvent e, int revisionNo) {
        Optional<Healer> reviewer = healers.findById(e.reviewerId());
        Optional<Healer> healer = healers.findById(e.healerId());
        if (reviewer.isEmpty() || healer.isEmpty()) {
            return null;
        }
        return emails.submitted(reviewer.get().getEmail(), reviewer.get().getFullName(),
                healer.get().getFullName(), revisionNo, appUrl, link("/reviews/", e.submissionId()));
    }

    private NotificationMessage decision(ReviewEvent e, ReviewStatus status) {
        Optional<Healer> healer = healers.findById(e.healerId());
        Optional<Healer> reviewer = healers.findById(e.reviewerId());
        if (healer.isEmpty() || reviewer.isEmpty()) {
            return null;
        }
        String link = link("/submissions/", e.submissionId());
        return status == ReviewStatus.APPROVED
                ? emails.approved(healer.get().getEmail(), healer.get().getFullName(),
                        reviewer.get().getFullName(), appUrl, link)
                : emails.changesRequested(healer.get().getEmail(), healer.get().getFullName(),
                        reviewer.get().getFullName(), appUrl, link);
    }

    private NotificationMessage reply(ReviewEvent e, AuthorSide actor) {
        boolean fromHealer = actor == AuthorSide.HEALER;
        Optional<Healer> to = healers.findById(fromHealer ? e.reviewerId() : e.healerId());
        Optional<Healer> from = healers.findById(fromHealer ? e.healerId() : e.reviewerId());
        if (to.isEmpty() || from.isEmpty()) {
            return null;
        }
        return emails.reply(to.get().getEmail(), to.get().getFullName(), from.get().getFullName(),
                appUrl, link(fromHealer ? "/reviews/" : "/submissions/", e.submissionId()));
    }

    private String link(String path, UUID submissionId) {
        return appUrl + path + submissionId;
    }
}
