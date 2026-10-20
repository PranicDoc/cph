package com.pranicdoc.cph.notification.channel;

/**
 * One message for one recipient on one channel. {@code recipient} is an email address or a phone
 * number depending on the channel; {@code subject} is only used by channels that have one (email).
 */
public record NotificationMessage(ChannelType channel, String recipient, String subject, String body) {

    public static NotificationMessage email(String to, String subject, String body) {
        return new NotificationMessage(ChannelType.EMAIL, to, subject, body);
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
