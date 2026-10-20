package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.config.NotificationProperties;
import com.pranicdoc.cph.notification.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxSweeperTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);
    private final NotificationProperties properties = new NotificationProperties();
    private final OutboxSweeper sweeper = new OutboxSweeper(repository, dispatcher,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), properties);

    @Test
    void deadLettersExhaustedRowsFirst_thenRetriesEveryCandidate() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repository.markExhaustedStuckAsDlq(any(), any(), eq(3))).thenReturn(2);
        when(repository.findSweepCandidates(any(), any(), eq(3), eq(100))).thenReturn(List.of(a, b));
        when(dispatcher.dispatch(a)).thenReturn(true);
        when(dispatcher.dispatch(b)).thenReturn(false);

        OutboxSweeper.Result result = sweeper.sweep();

        var order = inOrder(repository, dispatcher);
        order.verify(repository).markExhaustedStuckAsDlq(any(), any(), eq(3));
        order.verify(dispatcher).dispatch(a);
        order.verify(dispatcher).dispatch(b);
        assertThat(result).isEqualTo(new OutboxSweeper.Result(2, 2, 1));
    }

    @Test
    void oneBadRowDoesNotStopTheSweep() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repository.findSweepCandidates(any(), any(), eq(3), eq(100))).thenReturn(List.of(a, b));
        when(dispatcher.dispatch(a)).thenThrow(new IllegalStateException("boom"));
        when(dispatcher.dispatch(b)).thenReturn(true);

        OutboxSweeper.Result result = sweeper.sweep();

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.sent()).isEqualTo(1);
    }
}
