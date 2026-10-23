package com.pranicdoc.cph.auth.service;

import com.pranicdoc.cph.auth.service.impl.AccountEmails;
import com.pranicdoc.cph.notification.channel.ChannelType;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.email.EmailTemplates;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountEmailsTest {

    private static final String LINK = "https://app.healingark.in/accept-invite?token=abc123";

    private final AccountEmails emails = new AccountEmails(new EmailTemplates());

    @Nested
    class Invite {

        private final NotificationMessage invite = emails.invite("asha@example.com", LINK, 72);

        @Test
        void goesToTheInviteeWithTheInviteSubject() {
            assertEquals(ChannelType.EMAIL, invite.channel());
            assertEquals("asha@example.com", invite.recipient());
            assertEquals("You're invited to HealingARK", invite.subject());
        }

        @Test
        void bothVersionsCarryTheLinkAndTheExpiry() {
            assertTrue(invite.body().contains(LINK));
            assertTrue(invite.body().contains("expires in 72 hours"));
            assertTrue(invite.htmlBody().contains("href=\"" + LINK + "\""));
            assertTrue(invite.htmlBody().contains("expires in 72 hours"));
        }

        @Test
        void htmlIsBrandedWithTheLogoFromTheAppHost() {
            assertTrue(invite.htmlBody().contains("src=\"https://app.healingark.in/email/healingark-mark.png\""));
            assertTrue(invite.htmlBody().contains("Set up my account"));
            assertFalse(invite.htmlBody().contains("PranicDoc"));
        }
    }

    @Nested
    class SelfSignupVerification {

        private final NotificationMessage verify = emails.selfSignupVerification("ravi@example.com", LINK, 48);

        @Test
        void asksThemToVerifyWithTheLink() {
            assertEquals("Verify your email to complete HealingARK registration", verify.subject());
            assertTrue(verify.body().contains(LINK));
            assertTrue(verify.htmlBody().contains("href=\"" + LINK + "\""));
            assertTrue(verify.htmlBody().contains("Verify my email"));
            assertTrue(verify.htmlBody().contains("expires in 48 hours"));
        }
    }

    @Nested
    class Welcome {

        private final NotificationMessage welcome =
                emails.welcome("asha@example.com", "Asha Rao", "https://app.healingark.in/accept-invite");

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
            assertTrue(welcome.htmlBody().contains("Dear Asha Rao,"));
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
            assertTrue(welcome.htmlBody().contains("href=\"https://app.healingark.in\""));
            assertFalse(welcome.htmlBody().contains("accept-invite"));
        }

        @Test
        void aNameWithMarkupIsEscapedNotRendered() {
            NotificationMessage hostile =
                    emails.welcome("x@example.com", "<b>Eve</b>", "https://app.healingark.in/accept-invite");
            assertFalse(hostile.htmlBody().contains("<b>Eve</b>"));
            assertTrue(hostile.htmlBody().contains("&lt;b&gt;Eve&lt;/b&gt;"));
        }
    }
}
