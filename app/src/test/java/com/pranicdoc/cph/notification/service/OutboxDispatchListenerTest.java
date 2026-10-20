package com.pranicdoc.cph.notification.service;

import com.pranicdoc.cph.notification.config.NotificationProperties;
import com.pranicdoc.cph.notification.config.NotificationProperties.DispatchMode;
import com.pranicdoc.cph.notification.event.OutboxCreatedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatchListenerTest {

    private final OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor recordingExecutor = queued::add;
    private final UUID id = UUID.randomUUID();

    @Test
    void syncMode_dispatchesInline() {
        listener(DispatchMode.SYNC).onOutboxCreated(new OutboxCreatedEvent(id));

        verify(dispatcher).dispatch(id);
        assertThat(queued).isEmpty();
    }

    @Test
    void asyncMode_handsOffToExecutor() {
        listener(DispatchMode.ASYNC).onOutboxCreated(new OutboxCreatedEvent(id));

        verify(dispatcher, never()).dispatch(id);
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        verify(dispatcher).dispatch(id);
    }

    @Test
    void failuresNeverReachTheCaller_sweeperRetriesLater() {
        when(dispatcher.dispatch(id)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener(DispatchMode.SYNC).onOutboxCreated(new OutboxCreatedEvent(id)))
                .doesNotThrowAnyException();
    }

    @Test
    void asyncMode_executorRejectionIsSwallowed() {
        Executor full = task -> { throw new java.util.concurrent.RejectedExecutionException("queue full"); };
        NotificationProperties props = new NotificationProperties();
        props.setDispatch(DispatchMode.ASYNC);

        assertThatCode(() -> new OutboxDispatchListener(dispatcher, full, props)
                .onOutboxCreated(new OutboxCreatedEvent(id)))
                .doesNotThrowAnyException();
    }

    private OutboxDispatchListener listener(DispatchMode mode) {
        NotificationProperties props = new NotificationProperties();
        props.setDispatch(mode);
        return new OutboxDispatchListener(dispatcher, recordingExecutor, props);
    }
}
