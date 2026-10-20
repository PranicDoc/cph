package com.pranicdoc.cph.notification.channel;

/**
 * Strategy for delivering a {@link NotificationMessage} over one channel. Implementations are
 * Spring beans; {@code NotificationSender} picks the one whose {@link #type()} matches the message.
 */
public interface NotificationChannel {

    ChannelType type();

    /** @throws NotificationDeliveryException when the message could not be handed to the provider */
    void send(NotificationMessage message);
}
