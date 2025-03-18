package com.phonepe.sentinelai.core.events;

import com.google.common.annotations.VisibleForTesting;
import io.appform.signals.signals.ConsumingFireForgetSignal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *  The common bus which is used to manage signal emission and handling
 */
public class EventBus {
    private final ConsumingFireForgetSignal<AgentEvent> eventSignal;

    /**
     * Create event bus with default cached thread pool executor service
     */
    public EventBus() {
        this(Executors.newCachedThreadPool());
    }

    /**
     * Create event bus with custom executor service
     * @param executorService The executor service to use for handling events
     */
    public EventBus(final ExecutorService executorService) {
        this(ConsumingFireForgetSignal.<AgentEvent>builder()
                     .executorService(executorService)
                     .build());
    }

    @VisibleForTesting
    EventBus(ConsumingFireForgetSignal<AgentEvent> eventSignal) {
        this.eventSignal = eventSignal;
    }

    /**
     * @return The signal to listen to events. USe Signal.connect to connect event handlers.
     */
    public ConsumingFireForgetSignal<AgentEvent> onEvent() {
        return eventSignal;
    }

    public void notify(final AgentEvent event) {
        eventSignal.dispatch(event);
    }
}
