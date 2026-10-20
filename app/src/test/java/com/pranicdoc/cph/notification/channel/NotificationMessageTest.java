package com.pranicdoc.cph.notification.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NotificationMessageTest {

    @Test
    void aPhoneNumberIsMaskedExceptItsLastTwoDigits() {
        assertEquals("***********99", NotificationMessage.whatsapp("+919999999999", "hi").maskedRecipient());
        assertFalse(NotificationMessage.whatsapp("+919999999999", "hi").maskedRecipient().contains("9999999"));
    }

    @Test
    void anEmailKeepsOnlyItsFirstLetterAndDomain() {
        assertEquals("a***@example.com", NotificationMessage.email("asha@example.com", "s", "b").maskedRecipient());
    }

    @Test
    void aMissingRecipientIsShownAsNone() {
        assertEquals("(none)", new NotificationMessage(ChannelType.EMAIL, null, "s", "b").maskedRecipient());
    }
}
