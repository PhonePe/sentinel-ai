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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link PendingMessagesQueue} and {@link PendingMessageInjector}.
 */
class PendingMessageInjectorTest {

    private static AgentMessage systemPrompt() {
        return new SystemPrompt("session-1", "run-1", "system prompt", false, null);
    }

    private static AgentMessage userPrompt(final String content) {
        return UserPrompt.text("session-1", "run-1", content, LocalDateTime.now());
    }

    @Test
    void emptyQueueIsANoOp() {
        final var queue = new PendingMessagesQueue();
        final var injector = new PendingMessageInjector(queue);
        final var allMessages = List.of(systemPrompt(), userPrompt("hello"));
        final var newMessages = List.<AgentMessage>of(userPrompt("hello"));

        final var result = injector.process(null, allMessages, newMessages);

        assertSame(allMessages, result.getTransformedMessages());
        assertSame(newMessages, result.getNewMessages());
    }

    @Test
    void nullAddIsIgnoredAndDrainOnEmptyReturnsEmpty() {
        final var queue = new PendingMessagesQueue();
        queue.add(null);
        assertTrue(queue.drain().isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void pendingMessagesAreAppendedAtTheEndAndDrained() {
        final var queue = new PendingMessagesQueue();
        final var injector = new PendingMessageInjector(queue);
        final var pending1 = userPrompt("additional input 1");
        final var pending2 = UserPrompt.imageData("session-1",
                                                  "run-1",
                                                  "data:image/png;base64,aGVsbG8=",
                                                  ImageDetail.HIGH,
                                                  LocalDateTime.now());
        queue.add(pending1);
        queue.add(pending2);
        assertEquals(2, queue.size());

        final var baseMessages = List.of(systemPrompt(), userPrompt("hello"));
        final var baseNewMessages = List.<AgentMessage>of(userPrompt("hello"));

        final var result = injector.process(null, baseMessages, baseNewMessages);

        assertEquals(4, result.getTransformedMessages().size());
        assertSame(baseMessages.get(0), result.getTransformedMessages().get(0));
        assertSame(baseMessages.get(1), result.getTransformedMessages().get(1));
        assertSame(pending1, result.getTransformedMessages().get(2));
        assertSame(pending2, result.getTransformedMessages().get(3));

        assertEquals(3, result.getNewMessages().size());
        assertSame(baseNewMessages.get(0), result.getNewMessages().get(0));
        assertSame(pending1, result.getNewMessages().get(1));
        assertSame(pending2, result.getNewMessages().get(2));

        assertTrue(queue.drain().isEmpty(), "queue should be drained after injection");
    }
}
