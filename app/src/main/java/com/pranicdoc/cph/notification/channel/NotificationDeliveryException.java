package com.pranicdoc.cph.notification.channel;

/** A channel failed to hand a message to its provider (SMTP, WhatsApp API, ...). */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
