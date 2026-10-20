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
}
