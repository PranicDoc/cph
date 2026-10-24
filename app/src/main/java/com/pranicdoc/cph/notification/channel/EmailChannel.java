package com.pranicdoc.cph.notification.channel;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Email over SMTP. JavaMail has no implicit From (it would synthesize <os-user>@<container-hostname>,
 * which relays reject), so every message sets {@code app.mail.from} explicitly. That address is
 * independent of the SMTP login; the SMTP account must be allowed to send as it.
 */
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    /** An explicitly blank APP_MAIL_FROM resolves fine as a placeholder; refuse to start instead of mailing from nowhere. */
    @PostConstruct
    void validateConfig() {
        if (StringUtils.isBlank(fromAddress)) {
            throw new IllegalStateException("app.mail.from must be a non-blank address - it is the From of every email. Refusing to start.");
        }
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(NotificationMessage message) {
        if (message.channel() != ChannelType.EMAIL) {
            throw new IllegalArgumentException("EmailChannel cannot send a " + message.channel() + " message");
        }
        try {
            if (message.htmlBody() != null) {
                mailSender.send(htmlMail(message));
                return;
            }
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromAddress);
            mail.setTo(message.recipient());
            mail.setSubject(message.subject());
            mail.setText(message.body());
            mailSender.send(mail);
        } catch (MailException ex) {
            throw new NotificationDeliveryException("Email to " + message.maskedRecipient() + " failed: " + ex.getMessage(), ex);
        }
    }

    /** multipart/alternative: the plain-text body and the HTML body, so every client has a version it can show. */
    private MimeMessage htmlMail(NotificationMessage message) {
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.body(), message.htmlBody());
        } catch (MessagingException ex) {
            throw new MailPreparationException("Could not build the email", ex);
        }
        return mime;
    }
}
