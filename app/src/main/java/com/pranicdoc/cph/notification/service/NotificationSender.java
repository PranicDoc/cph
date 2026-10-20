package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.notification.channel.ChannelType;
import com.pranicdoc.cph.notification.channel.NotificationChannel;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The one entry point features use to notify people. It picks the channel strategy for the
 * message and never throws: a mail or WhatsApp outage must not break the business action that
 * triggered the notification.
 *
 * <ul>
 *   <li>{@link #sendNow} delivers on the caller's thread and reports whether it worked (for callers
 *       that tell the user, e.g. the invite's {@code emailSent}).</li>
 *   <li>{@link #sendAfterCommit} waits for the caller's transaction to commit, then delivers on the
 *       notification executor. Use inside {@code @Transactional} code.</li>
 * </ul>
 */
@Slf4j
@Service
public class NotificationSender {

    private final Map<ChannelType, NotificationChannel> channels = new EnumMap<>(ChannelType.class);
    private final TransactionService transactionService;
    private final Executor executor;

    public NotificationSender(List<NotificationChannel> channelStrategies,
                              TransactionService transactionService,
                              @Qualifier("notificationExecutor") Executor executor) {
        channelStrategies.forEach(c -> channels.put(c.type(), c));
        this.transactionService = transactionService;
        this.executor = executor;
    }

    public boolean sendNow(NotificationMessage message) {
        NotificationChannel channel = channels.get(message.channel());
        if (channel == null) {
            log.warn("No {} channel is configured; message to {} not sent", message.channel(), message.maskedRecipient());
            return false;
        }
        try {
            channel.send(message);
            return true;
        } catch (RuntimeException ex) {
            log.warn("{} notification to {} failed: {}", message.channel(), message.maskedRecipient(), ex.getMessage());
            return false;
        }
    }

    public void sendAfterCommit(NotificationMessage message) {
        transactionService.runAfterCommit(() -> executor.execute(() -> sendNow(message)));
    }
}
