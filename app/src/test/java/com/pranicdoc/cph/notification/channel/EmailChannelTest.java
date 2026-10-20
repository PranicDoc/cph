package com.pranicdoc.cph.notification.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailChannelTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmailChannel(mailSender);
        ReflectionTestUtils.setField(channel, "fromAddress", "hello@healingark.in");
    }

    @Test
    void isTheEmailStrategy() {
        assertEquals(ChannelType.EMAIL, channel.type());
    }

    @Test
    void sendsFromTheConfiguredAddress() {
        channel.send(NotificationMessage.email("asha@example.com", "Hello", "Body text"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertEquals("hello@healingark.in", mail.getFrom());
        assertArrayEquals(new String[]{"asha@example.com"}, mail.getTo());
        assertEquals("Hello", mail.getSubject());
        assertEquals("Body text", mail.getText());
    }

    @Test
    void aMailFailureSurfacesAsADeliveryException() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(NotificationDeliveryException.class,
                () -> channel.send(NotificationMessage.email("asha@example.com", "Hello", "Body")));
    }

    @Test
    void refusesAMessageForAnotherChannel() {
        assertThrows(IllegalArgumentException.class,
                () -> channel.send(new NotificationMessage(ChannelType.WHATSAPP, "+911234567890", null, "hi")));
    }
}
