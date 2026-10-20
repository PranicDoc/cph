package com.pranicdoc.cph.auth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;

/**
 * Welcome email for a healer who has just activated their account through the registration
 * link. Runs on the async executor and never throws: a slow or failing mail server must not
 * delay or undo the activation (same stance as the invite emails).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeEmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFromAddress;

    /** The activation page URL; the welcome email links to the app root on the same host. */
    @Value("${app.invite.url}")
    private String inviteUrl;

    @Async
    public void sendWelcome(String email, String fullName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFromAddress);
            message.setTo(email);
            message.setSubject("Welcome to PranicDoc");
            message.setText(
                    "Hi " + fullName + ",\n\n"
                            + "Welcome to PranicDoc - your account is ready.\n\n"
                            + "Sign in any time at " + appUrl() + "\n\n"
                            + "If you need a hand getting started, just reply to this email.\n\n"
                            + "The PranicDoc team");
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to send welcome email to {}: {}", email, ex.getMessage());
        }
    }

    private String appUrl() {
        URI uri = URI.create(inviteUrl);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
