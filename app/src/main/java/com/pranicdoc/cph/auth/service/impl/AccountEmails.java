package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.email.EmailTemplates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * The account emails (invite, self-signup verification, welcome): wording and links, kept apart from
 * the sending so they are easy to edit. Each is sent as HTML (templates/email/) with the plain-text
 * version alongside.
 */
@Component
@RequiredArgsConstructor
public class AccountEmails {

    /** Served by the UI from ui/public/email/, so it lives on the same host as the links. */
    private static final String LOGO_PATH = "/email/healingark-mark.png";

    private final EmailTemplates templates;

    /** An admin invited this address to join as a healer. */
    public NotificationMessage invite(String email, String inviteLink, long expiryHours) {
        String text = "You've been invited to join HealingARK.\n\n"
                + "Click the link below to set up your account:\n"
                + inviteLink + "\n\n"
                + "This link expires in " + expiryHours + " hours.";
        Map<String, Object> vars = common(inviteLink);
        vars.put("link", inviteLink);
        vars.put("expiryHours", expiryHours);
        return NotificationMessage.email(email, "You're invited to HealingARK", text,
                templates.render("invite", vars));
    }

    /** Someone signed up on their own; they confirm the address before registering. */
    public NotificationMessage selfSignupVerification(String email, String verifyLink, long expiryHours) {
        String text = "You requested to register as a healer on HealingARK.\n\n"
                + "Click the link below to verify your email and complete registration:\n"
                + verifyLink + "\n\n"
                + "This link expires in " + expiryHours + " hours.";
        Map<String, Object> vars = common(verifyLink);
        vars.put("link", verifyLink);
        vars.put("expiryHours", expiryHours);
        return NotificationMessage.email(email, "Verify your email to complete HealingARK registration", text,
                templates.render("signup-verification", vars));
    }

    /** The healer asked to reset a forgotten password. */
    public NotificationMessage passwordReset(String email, String resetLink, long expiryMinutes) {
        String text = "We received a request to reset your HealingARK password.\n\n"
                + "Click the link below to choose a new one:\n"
                + resetLink + "\n\n"
                + "This link expires in " + expiryMinutes + " minutes and can be used once. "
                + "If you didn't ask for this, you can ignore this email - your password stays as it is.";
        Map<String, Object> vars = common(resetLink);
        vars.put("link", resetLink);
        vars.put("expiryMinutes", expiryMinutes);
        return NotificationMessage.email(email, "Reset your HealingARK password", text,
                templates.render("password-reset", vars));
    }

    /** Sent after a reset, so a healer who did not do it knows straight away. */
    public NotificationMessage passwordChanged(String email, String loginLink) {
        String text = "Your HealingARK password was just changed.\n\n"
                + "If this was you, there is nothing more to do. If it wasn't, reset your password right away: "
                + loginLink + " (choose \"Forgot password?\") and reply to this email.";
        Map<String, Object> vars = common(loginLink);
        vars.put("link", loginLink);
        return NotificationMessage.email(email, "Your HealingARK password was changed", text,
                templates.render("password-changed", vars));
    }

    /**
     * Welcome for a healer who has just activated their account. The link goes to the app root on
     * the host of {@code inviteUrl} (the one-time activation page is no use after activation).
     */
    public NotificationMessage welcome(String email, String fullName, String inviteUrl) {
        String appUrl = appUrl(inviteUrl);
        String text = "Dear " + fullName + ",\n\n"
                + "Welcome - you are now part of the HealingARK community of healers, and we are truly glad you are here.\n\n"
                + "You do your best work when you are with your clients, not with paperwork. We are here to make "
                + "documenting your cases simpler, and we will keep improving it with you. So please don't worry "
                + "about getting everything right from day one. Take your time, explore, and if you ever feel "
                + "stuck, just reply to this email - a real person will help you.\n\n"
                + "Your space is ready whenever you are: " + appUrl + "\n\n"
                + "With care,\n"
                + "Team HealingARK";
        Map<String, Object> vars = common(inviteUrl);
        vars.put("fullName", fullName);
        return NotificationMessage.email(email, "Welcome to the HealingARK family", text,
                templates.render("welcome", vars));
    }

    private static Map<String, Object> common(String anyAppLink) {
        String appUrl = appUrl(anyAppLink);
        Map<String, Object> vars = new HashMap<>();
        vars.put("appUrl", appUrl);
        vars.put("logoUrl", appUrl + LOGO_PATH);
        return vars;
    }

    private static String appUrl(String anyAppLink) {
        URI uri = URI.create(anyAppLink);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
