package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.common.util.TransactionService;
import com.pranicdoc.cph.notification.channel.ChannelType;
import com.pranicdoc.cph.notification.channel.NotificationChannel;
import com.pranicdoc.cph.notification.channel.NotificationDeliveryException;
import com.pranicdoc.cph.notification.channel.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationSenderTest {

    @Mock
    private NotificationChannel emailChannel;

    @Mock
    private TransactionService transactionService;

    private NotificationSender sender;

    private final NotificationMessage welcome = NotificationMessage.email("asha@example.com", "Hi", "Body");

    @BeforeEach
    void setUp() {
        when(emailChannel.type()).thenReturn(ChannelType.EMAIL);
        // direct executor: "async" work runs inline so the test can observe it
        sender = new NotificationSender(List.of(emailChannel), transactionService, Runnable::run);
    }

    @Test
    void sendNowRoutesToTheChannelOfTheMessageAndReportsSuccess() {
        assertTrue(sender.sendNow(welcome));

        verify(emailChannel).send(welcome);
    }

    @Test
    void sendNowReportsFailureInsteadOfThrowing() {
        doThrow(new NotificationDeliveryException("smtp down", null)).when(emailChannel).send(any());

        assertFalse(sender.sendNow(welcome));
    }

    @Test
    void aChannelThatIsNotConfiguredIsAFailureNotACrash() {
        NotificationMessage whatsapp = new NotificationMessage(ChannelType.WHATSAPP, "+911234567890", null, "hi");

        assertFalse(sender.sendNow(whatsapp));
        verify(emailChannel, never()).send(any());
    }

    @Test
    void sendAfterCommitWaitsForTheCommitThenDeliversOffTheCallersThread() {
        sender.sendAfterCommit(welcome);

        ArgumentCaptor<Runnable> afterCommit = ArgumentCaptor.forClass(Runnable.class);
        verify(transactionService).runAfterCommit(afterCommit.capture());
        verify(emailChannel, never()).send(any());   // nothing before the commit
        afterCommit.getValue().run();
        verify(emailChannel).send(welcome);
    }

    @Test
    void sendAfterCommitNeverThrowsEvenWhenDeliveryFails() {
        doThrow(new NotificationDeliveryException("smtp down", null)).when(emailChannel).send(any());
        sender.sendAfterCommit(welcome);
        ArgumentCaptor<Runnable> afterCommit = ArgumentCaptor.forClass(Runnable.class);
        verify(transactionService).runAfterCommit(afterCommit.capture());

        afterCommit.getValue().run();   // must not throw
    }
}
