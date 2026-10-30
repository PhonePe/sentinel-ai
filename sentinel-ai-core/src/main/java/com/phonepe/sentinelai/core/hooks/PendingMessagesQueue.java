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

import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A thread-safe queue of {@link AgentMessage}s that are injected into the next model call.
 *
 * <p>Messages added to this queue are picked up by {@link PendingMessageInjector}, which is
 * an {@link AgentMessagesPreProcessor} registered on the agent, and appended to the end of
 * the message list sent to the model on the next (or current, if a tool loop is in flight)
 * request. Each message is delivered exactly once: it is drained from the queue when it is
 * picked up for sending.
 *
 * <p>Typical producers are tools that need to attach media (e.g. a {@code read_image} tool
 * enqueuing a user-role image message) and host applications that want to feed additional
 * user input into an in-flight run without interrupting it.
 */
@ToString
public class PendingMessagesQueue {

    private final ConcurrentLinkedQueue<AgentMessage> pending = new ConcurrentLinkedQueue<>();

    /**
     * Adds a message to the tail of the queue.
     *
     * @param message the message to deliver on the next model call
     */
    public void add(final AgentMessage message) {
        if (message != null) {
            pending.add(message);
        }
    }

    /**
     * Drains all pending messages, preserving queue order, and returns them. Drained
     * messages are removed from the queue (delivered exactly once).
     *
     * @return the drained messages in FIFO order; empty if the queue is empty
     */
    public List<AgentMessage> drain() {
        if (pending.isEmpty()) {
            return List.of();
        }
        final var drained = new ArrayList<AgentMessage>();
        AgentMessage message;
        while ((message = pending.poll()) != null) {
            drained.add(message);
        }
        return drained;
    }

    /**
     * Returns the number of messages currently waiting to be delivered.
     *
     * @return the pending message count
     */
    public int size() {
        return pending.size();
    }
}
