/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
