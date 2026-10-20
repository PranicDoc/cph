package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.channel.NotificationMessage;
import com.pranicdoc.cph.notification.channel.WhatsappChannel;
import com.pranicdoc.cph.notification.config.NotificationProperties;
import com.pranicdoc.cph.notification.entity.NotificationOutbox;
import com.pranicdoc.cph.notification.entity.NotificationOutbox.NotificationStatus;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final WhatsappChannel sender = mock(WhatsappChannel.class);
    private final NotificationProperties properties = new NotificationProperties();
    private OutboxDispatcher dispatcher;

    private final UUID id = UUID.randomUUID();
    private NotificationOutbox row;

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxDispatcher(repository, sender,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), properties);
        row = new NotificationOutbox("+919999999999", "hello");
        row.setId(id);
        row.setStatus(NotificationStatus.PROCESSING);
        when(repository.findById(id)).thenReturn(Optional.of(row));
    }

    @Test
    void doesNothingWhenAnotherWorkerOwnsTheRow() {
        when(repository.claim(eq(id), any(), any(), anyInt())).thenReturn(0);

        assertThat(dispatcher.dispatch(id)).isFalse();

        verify(sender, never()).send(any(NotificationMessage.class));
        verify(repository, never()).save(any());
    }

    @Test
    void marksProcessedWhenSendSucceeds() {
        when(repository.claim(eq(id), any(), any(), eq(3))).thenReturn(1);

        assertThat(dispatcher.dispatch(id)).isTrue();

        verify(sender).send(NotificationMessage.whatsapp("+919999999999", "hello"));
        assertThat(saved().getStatus()).isEqualTo(NotificationStatus.PROCESSED);
    }

    @Test
    void marksFailedAndCountsAttemptWhenSendFailsWithRetriesLeft() {
        when(repository.claim(eq(id), any(), any(), anyInt())).thenReturn(1);
        doThrow(new RuntimeException("WhatsApp down")).when(sender).send(any(NotificationMessage.class));

        assertThat(dispatcher.dispatch(id)).isFalse();

        NotificationOutbox saved = saved();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(1);
    }

    @Test
    void movesToDlqWhenLastRetryFails() {
        row.setRetryCount(2);
        when(repository.claim(eq(id), any(), any(), anyInt())).thenReturn(1);
        doThrow(new RuntimeException("WhatsApp down")).when(sender).send(any(NotificationMessage.class));

        dispatcher.dispatch(id);

        NotificationOutbox saved = saved();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.DLQ);
        assertThat(saved.getRetryCount()).isEqualTo(3);
    }

    private NotificationOutbox saved() {
        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
