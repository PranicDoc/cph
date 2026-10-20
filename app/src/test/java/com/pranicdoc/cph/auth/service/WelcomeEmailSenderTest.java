package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.service.impl.WelcomeEmailSender;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WelcomeEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private WelcomeEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new WelcomeEmailSender(mailSender);
        ReflectionTestUtils.setField(sender, "mailFromAddress", "hello@healingark.in");
        ReflectionTestUtils.setField(sender, "inviteUrl", "https://app.healingark.in/accept-invite");
    }

    @Test
    void sendsFromTheConfiguredAddressToTheNewHealerWithAGreetingAndTheAppLink() {
        sender.sendWelcome("new@example.com", "Asha Rao");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertEquals("hello@healingark.in", mail.getFrom());
        assertArrayEquals(new String[]{"new@example.com"}, mail.getTo());
        assertEquals("Welcome to PranicDoc", mail.getSubject());
        assertTrue(mail.getText().contains("Asha Rao"));
        assertTrue(mail.getText().contains("https://app.healingark.in"));
        assertTrue(!mail.getText().contains("accept-invite"), "link is the app, not the one-time activation page");
    }

    @Test
    void aMailFailureIsLoggedNotThrown() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> sender.sendWelcome("new@example.com", "Asha Rao"));
    }
}
