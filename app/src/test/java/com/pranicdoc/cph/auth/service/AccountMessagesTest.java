package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.service.impl.AccountMessages;
import com.pranicdoc.cph.notification.channel.ChannelType;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountMessagesTest {

    private final NotificationMessage welcome =
            AccountMessages.welcome("asha@example.com", "Asha Rao", "https://app.healingark.in/accept-invite");

    @Test
    void goesToTheNewHealerByEmail() {
        assertEquals(ChannelType.EMAIL, welcome.channel());
        assertEquals("asha@example.com", welcome.recipient());
        assertEquals("Welcome to the HealingARK family", welcome.subject());
    }

    @Test
    void greetsThemByNameAndWelcomesThemIntoTheCommunity() {
        assertTrue(welcome.body().startsWith("Dear Asha Rao,"));
        assertTrue(welcome.body().contains("community"));
    }

    @Test
    void reassuresThemAndPromisesToKeepImprovingTheProcess() {
        assertTrue(welcome.body().contains("don't worry"));
        assertTrue(welcome.body().contains("simpler"));
        assertTrue(welcome.body().contains("reply to this email"));
    }

    @Test
    void linksToTheAppRootNotTheOneTimeActivationPage() {
        assertTrue(welcome.body().contains("https://app.healingark.in"));
        assertFalse(welcome.body().contains("accept-invite"));
    }
}
