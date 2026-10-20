package com.pranicdoc.cph.auth.service.impl;

import com.pranicdoc.cph.notification.channel.NotificationMessage;

import java.net.URI;

/** Wording of the account emails (welcome). Kept apart from the sending so it is easy to edit. */
public final class AccountMessages {

    private AccountMessages() {
    }

    /**
     * Welcome for a healer who has just activated their account. The link goes to the app root on
     * the host of {@code inviteUrl} (the one-time activation page is no use after activation).
     */
    public static NotificationMessage welcome(String email, String fullName, String inviteUrl) {
        URI uri = URI.create(inviteUrl);
        String appUrl = uri.getScheme() + "://" + uri.getAuthority();
        String body = "Dear " + fullName + ",\n\n"
                + "Welcome - you are now part of the HealingARK community of healers, and we are truly glad you are here.\n\n"
                + "You do your best work when you are with your clients, not with paperwork. We are here to make "
                + "documenting your cases simpler, and we will keep improving it with you. So please don't worry "
                + "about getting everything right from day one. Take your time, explore, and if you ever feel "
                + "stuck, just reply to this email - a real person will help you.\n\n"
                + "Your space is ready whenever you are: " + appUrl + "\n\n"
                + "With care,\n"
                + "Team HealingARK";
        return NotificationMessage.email(email, "Welcome to the HealingARK family", body);
    }
}
