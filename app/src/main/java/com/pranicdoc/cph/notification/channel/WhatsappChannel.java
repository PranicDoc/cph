package com.pranicdoc.cph.notification.channel;

import com.pranicdoc.cph.notification.service.WhatsappSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** WhatsApp text messages over the WhatsApp Cloud API (the call itself lives in {@link WhatsappSender}). */
@Component
@RequiredArgsConstructor
public class WhatsappChannel implements NotificationChannel {

    private final WhatsappSender whatsappSender;

    @Override
    public ChannelType type() {
        return ChannelType.WHATSAPP;
    }

    @Override
    public void send(NotificationMessage message) {
        if (message.channel() != ChannelType.WHATSAPP) {
            throw new IllegalArgumentException("WhatsappChannel cannot send a " + message.channel() + " message");
        }
        try {
            whatsappSender.send(message.recipient(), message.body());
        } catch (RuntimeException ex) {
            // The phone number is patient data: keep it out of the message and the logs.
            throw new NotificationDeliveryException(
                    "WhatsApp to " + message.maskedRecipient() + " failed: " + ex.getMessage(), ex);
        }
    }
}
