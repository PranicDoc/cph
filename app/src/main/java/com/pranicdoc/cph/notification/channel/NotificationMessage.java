package com.pranicdoc.cph.notification.channel;

/**
 * One message for one recipient on one channel. {@code recipient} is an email address or a phone
 * number depending on the channel; {@code subject} is only used by channels that have one (email).
 * {@code htmlBody} is an optional HTML version for email; {@code body} stays the plain-text version.
 */
public record NotificationMessage(ChannelType channel, String recipient, String subject, String body, String htmlBody) {

    public NotificationMessage(ChannelType channel, String recipient, String subject, String body) {
        this(channel, recipient, subject, body, null);
    }

    public static NotificationMessage email(String to, String subject, String body) {
        return new NotificationMessage(ChannelType.EMAIL, to, subject, body);
    }

    /** Email with an HTML version; {@code text} is sent alongside it for clients that show plain text. */
    public static NotificationMessage email(String to, String subject, String text, String html) {
        return new NotificationMessage(ChannelType.EMAIL, to, subject, text, html);
    }

    public static NotificationMessage whatsapp(String phoneNumber, String body) {
        return new NotificationMessage(ChannelType.WHATSAPP, phoneNumber, null, body);
    }

    /** The recipient safe to log: a phone number keeps its last two digits, an email its first letter and domain. */
    public String maskedRecipient() {
        if (recipient == null || recipient.isBlank()) {
            return "(none)";
        }
        int at = recipient.indexOf('@');
        if (channel == ChannelType.EMAIL && at > 0) {
            return recipient.charAt(0) + "***" + recipient.substring(at);
        }
        int keep = Math.min(2, recipient.length());
        return "*".repeat(recipient.length() - keep) + recipient.substring(recipient.length() - keep);
    }
}
