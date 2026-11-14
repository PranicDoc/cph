package com.pranicdoc.cph.review.notify;

import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.email.EmailTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * The case-review emails: wording only, kept apart from the routing in {@link ReviewNotifier}. Each is
 * HTML (templates/email/review-*.html) with a plain-text twin. None carries patient details or comment
 * text: the link takes the reader to the app, where access is checked.
 */
@Component
@RequiredArgsConstructor
public class ReviewEmails {

    private static final String LOGO_PATH = "/email/healingark-mark.png";
    private static final String SUBJECT_PREFIX = "HealingARK: ";

    private final EmailTemplates templates;

    /** A healer submitted (revision 1) or resubmitted (later revisions) a case; goes to their instructor. */
    public NotificationMessage submitted(String to, String toName, String healerName, int revisionNo,
                                         String appUrl, String link) {
        boolean resubmitted = revisionNo > 1;
        String heading = resubmitted
                ? "A case was resubmitted for your review"
                : "A case was submitted for your review";
        String message = resubmitted
                ? healerName + " resubmitted a case after making changes (version " + revisionNo + ")."
                : healerName + " submitted a case for your review.";
        return build(to, toName, heading, message, "Open the review", "review-submitted", appUrl, link);
    }

    /** The instructor asked for changes; goes to the healer. */
    public NotificationMessage changesRequested(String to, String toName, String reviewerName,
                                                String appUrl, String link) {
        return build(to, toName, "Changes requested on your case",
                reviewerName + " asked for changes on a case you submitted. Open it to see the feedback.",
                "View the feedback", "review-changes-requested", appUrl, link);
    }

    /** The instructor approved the case; goes to the healer. */
    public NotificationMessage approved(String to, String toName, String reviewerName,
                                        String appUrl, String link) {
        return build(to, toName, "Your case was approved",
                reviewerName + " approved a case you submitted. Well done.",
                "View the submission", "review-approved", appUrl, link);
    }

    /** A new comment from the other party. */
    public NotificationMessage reply(String to, String toName, String fromName,
                                     String appUrl, String link) {
        return build(to, toName, "New reply on a case review",
                fromName + " added a comment on a case review.",
                "Open the conversation", "review-reply", appUrl, link);
    }

    private NotificationMessage build(String to, String toName, String heading, String message,
                                      String buttonLabel, String template, String appUrl, String link) {
        String name = toName == null || toName.isBlank() ? "there" : toName;
        String text = "Hello " + name + ",\n\n" + message + "\n\n" + buttonLabel + ": " + link + "\n\n"
                + "With care,\nTeam HealingARK";
        Map<String, Object> vars = new HashMap<>();
        vars.put("appUrl", appUrl);
        vars.put("logoUrl", appUrl + LOGO_PATH);
        vars.put("heading", heading);
        vars.put("preheader", message);
        vars.put("toName", name);
        vars.put("message", message);
        vars.put("link", link);
        return NotificationMessage.email(to, SUBJECT_PREFIX + heading, text, templates.render(template, vars));
    }
}
