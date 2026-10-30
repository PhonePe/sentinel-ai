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

package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link AgentMessagesPreProcessor} that appends messages from a {@link PendingMessagesQueue}
 * to the end of the message list before every model call.
 *
 * <p>Because pre-processors run at the top of every model request iteration (including each
 * iteration of a tool-call loop), messages enqueued while a run is in flight are picked up by
 * the very next request: a tool that enqueues a message in one iteration will see it delivered
 * in the next iteration, and host input enqueued mid-run is delivered to the model without
 * stopping the run.
 *
 * <p>When the queue is empty this processor is a no-op and returns the input lists unchanged.
 */
@Slf4j
public class PendingMessageInjector implements AgentMessagesPreProcessor {

    private final PendingMessagesQueue queue;

    public PendingMessageInjector(@NonNull final PendingMessagesQueue queue) {
        this.queue = queue;
    }

    @Override
    public AgentMessagesPreProcessResult process(final AgentMessagesPreProcessContext ctx,
                                                 final List<AgentMessage> allMessages,
                                                 final List<AgentMessage> newMessages) {
        final var pendingMessages = queue.drain();
        if (pendingMessages.isEmpty()) {
            return new AgentMessagesPreProcessResult(allMessages, newMessages);
        }
        log.debug("Injecting {} pending message(s) into the next model call", pendingMessages.size());
        final var transformedAll = new ArrayList<AgentMessage>(allMessages);
        transformedAll.addAll(pendingMessages);
        final var transformedNew = new ArrayList<AgentMessage>(newMessages);
        transformedNew.addAll(pendingMessages);
        return new AgentMessagesPreProcessResult(List.copyOf(transformedAll), List.copyOf(transformedNew));
    }
}
