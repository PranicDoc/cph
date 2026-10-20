package com.pranicdoc.cph.notification.channel;

import com.pranicdoc.cph.notification.service.WhatsappSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WhatsappChannelTest {

    @Mock
    private WhatsappSender whatsappSender;

    private WhatsappChannel channel;

    @BeforeEach
    void setUp() {
        channel = new WhatsappChannel(whatsappSender);
    }

    @Test
    void isTheWhatsappStrategy() {
        assertEquals(ChannelType.WHATSAPP, channel.type());
    }

    @Test
    void sendsTheBodyToThePhoneNumber() {
        channel.send(NotificationMessage.whatsapp("+919999999999", "Hello from HealingARK"));

        verify(whatsappSender).send("+919999999999", "Hello from HealingARK");
    }

    @Test
    void aFailureSurfacesAsADeliveryExceptionWithoutTheFullPhoneNumber() {
        doThrow(new RuntimeException("graph api 500")).when(whatsappSender).send(anyString(), anyString());

        NotificationDeliveryException ex = assertThrows(NotificationDeliveryException.class,
                () -> channel.send(NotificationMessage.whatsapp("+919999999999", "hi")));

        assertFalse(ex.getMessage().contains("+919999999999"), "phone numbers are patient data: " + ex.getMessage());
    }

    @Test
    void refusesAMessageForAnotherChannel() {
        assertThrows(IllegalArgumentException.class,
                () -> channel.send(NotificationMessage.email("a@b.com", "s", "b")));
        verify(whatsappSender, never()).send(anyString(), anyString());
    }
}
