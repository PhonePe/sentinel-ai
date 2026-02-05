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

package com.phonepe.sentinelai.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.session.history.selectors.MessageSelector;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageReadingUtilsTest {

    private static final class InMemorySessionStore implements SessionStore {
        private final Map<String, List<AgentMessage>> messageData = new ConcurrentHashMap<>();

        @Override
        public boolean deleteSession(final String sessionId) {
            return false;
        }

        @Override
        public BiScrollable<AgentMessage> readMessages(final String sessionId,
                                                       final int count,
                                                       final boolean skipSystemPrompt,
                                                       final BiScrollable.DataPointer pointer,
                                                       final QueryDirection queryDirection) {
            // Get all messages for the session and sort them chronologically (oldest to newest)
            final var allMessages = messageData.getOrDefault(sessionId,
                                                             List.of())
                    .stream()
                    .sorted((m1, m2) -> Long.compare(m1.getTimestamp(),
                                                     m2.getTimestamp()))
                    .toList();

            List<AgentMessage> filtered;
            if (pointer == null) {
                filtered = allMessages;
            }
            else {
                if (queryDirection == QueryDirection.OLDER && pointer
                        .getOlder() != null) {
                    final var olderTimestamp = Long.parseLong(pointer
                            .getOlder());
                    filtered = allMessages.stream()
                            .filter(m -> m.getTimestamp() < olderTimestamp)
                            .toList();
                }
                else if (queryDirection == QueryDirection.NEWER && pointer
                        .getNewer() != null) {
                    final var newerTimestamp = Long.parseLong(pointer
                            .getNewer());
                    filtered = allMessages.stream()
                            .filter(m -> m.getTimestamp() > newerTimestamp)
                            .toList();
                }
                else {
                    filtered = allMessages;
                }
            }

            final List<AgentMessage> result;
            if (queryDirection == QueryDirection.OLDER) {
                // Return LAST 'count' messages in chronological order
                final var start = Math.max(0, filtered.size() - count);
                result = new ArrayList<>(filtered.subList(start,
                                                          filtered.size()));
            }
            else {
                // Return FIRST 'count' messages in chronological order
                result = filtered.stream().limit(count).toList();
            }

            String older = null;
            String newer = null;
            if (!result.isEmpty()) {
                // result is always oldest to newest
                older = String.valueOf(result.get(0).getTimestamp());
                newer = String.valueOf(result.get(result.size() - 1)
                        .getTimestamp());
            }

            return new BiScrollable<>(result,
                                      new BiScrollable.DataPointer(older,
                                                                   newer));
        }

        @Override
        public void saveMessages(final String sessionId,
                                 final String runId,
                                 final List<AgentMessage> messages) {
            messageData.computeIfAbsent(sessionId, k -> new ArrayList<>())
                    .addAll(messages);
        }

        @Override
        public Optional<SessionSummary> saveSession(final SessionSummary sessionSummary) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionSummary> session(final String sessionId) {
            return Optional.empty();
        }

        @Override
        public BiScrollable<SessionSummary> sessions(final int count,
                                                     final String pointer,
                                                     final QueryDirection queryDirection) {
            return null;
        }
    }

    private InMemorySessionStore sessionStore;
    private AgentSessionExtensionSetup setup;
    private final String sessionId = "session-1";

    @BeforeEach
    void setUp() {
        sessionStore = new InMemorySessionStore();
        setup = new AgentSessionExtensionSetup(2, 1000, 60);
    }

    @Test
    void testReadMessagesSinceIdWithPagination() {
        final var totalMessages = 10;
        final var messages = new ArrayList<>(IntStream.rangeClosed(1,
                                                                   totalMessages)
                .mapToObj(i -> (AgentMessage) UserPrompt.builder()
                        .sessionId(sessionId)
                        .runId("run-1")
                        .messageId("msg-" + i)
                        .timestamp((long) i)
                        .content("Hi " + i)
                        .sentAt(LocalDateTime.now())
                        .build())
                .toList());

        // Shuffle to ensure sorting works
        Collections.shuffle(messages);
        sessionStore.saveMessages(sessionId, "run-1", messages);

        // Read all since beginning
        final var resultAll = MessageReadingUtils.readMessagesSinceId(
                                                                      sessionStore,
                                                                      setup,
                                                                      sessionId,
                                                                      null,
                                                                      false,
                                                                      List.of());

        assertEquals(totalMessages, resultAll.size());

        // Verify strict chronological order
        IntStream.range(0, totalMessages).forEach(i -> {
            final var expectedId = "msg-" + (i + 1);
            assertEquals(expectedId,
                         resultAll.get(i).getMessageId(),
                         "Message at index " + i + " should be " + expectedId);
        });

        // Read since msg-5 (should return msg-6 to msg-10)
        final var resultSince = MessageReadingUtils.readMessagesSinceId(
                                                                        sessionStore,
                                                                        setup,
                                                                        sessionId,
                                                                        "msg-5",
                                                                        false,
                                                                        List.of());
        assertEquals(5, resultSince.size());
        IntStream.range(0, 5).forEach(i -> {
            final var expectedId = "msg-" + (i + 6);
            assertEquals(expectedId, resultSince.get(i).getMessageId());
        });
    }

    @Test
    void testReadMessagesWithSelectors() {
        final var messages = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> (AgentMessage) UserPrompt.builder()
                        .sessionId(sessionId)
                        .runId("run-1")
                        .messageId("msg-" + i)
                        .timestamp((long) i)
                        .content("Hi " + i)
                        .sentAt(LocalDateTime.now())
                        .build())
                .toList();
        sessionStore.saveMessages(sessionId, "run-1", messages);

        final MessageSelector selector = (sid, msgs) -> msgs.stream()
                .filter(m -> !m.getMessageId().equals("msg-2"))
                .toList();

        final var result = MessageReadingUtils.readMessagesSinceId(sessionStore,
                                                                   setup,
                                                                   sessionId,
                                                                   null,
                                                                   false,
                                                                   List.of(selector));

        assertEquals(2, result.size());
        assertEquals("msg-1", result.get(0).getMessageId());
        assertEquals("msg-3", result.get(1).getMessageId());
    }

    @Test
    void testRearrangeMessages() {
        final var userPrompt = UserPrompt.builder()
                .sessionId(sessionId)
                .runId("r1")
                .messageId("u1")
                .timestamp(1L)
                .content("hi")
                .build();

        final var tc1 = ToolCall.builder()
                .sessionId(sessionId)
                .runId("r1")
                .messageId("tc1")
                .timestamp(2L)
                .toolCallId("tcid-1")
                .toolName("tool1")
                .build();

        final var tc2 = ToolCall.builder()
                .sessionId(sessionId)
                .runId("r1")
                .messageId("tc2")
                .timestamp(3L)
                .toolCallId("tcid-2")
                .toolName("tool2")
                .build();

        final var tcr1 = ToolCallResponse.builder()
                .sessionId(sessionId)
                .runId("r1")
                .messageId("tcr1")
                .timestamp(4L)
                .toolCallId("tcid-1")
                .toolName("tool1")
                .response("res1")
                .build();

        final var tcr2 = ToolCallResponse.builder()
                .sessionId(sessionId)
                .runId("r1")
                .messageId("tcr2")
                .timestamp(5L)
                .toolCallId("tcid-2")
                .toolName("tool2")
                .response("res2")
                .build();

        // Mixed order: u1, tc1, tc2, tcr1, tcr2
        final List<AgentMessage> messages = List.of(userPrompt,
                                                    tc1,
                                                    tc2,
                                                    tcr1,
                                                    tcr2);

        final var rearranged = MessageReadingUtils.rearrangeMessages(messages);

        assertEquals(5, rearranged.size());
        assertEquals("u1", rearranged.get(0).getMessageId());
        assertEquals("tc1", rearranged.get(1).getMessageId());
        assertEquals("tcr1", rearranged.get(2).getMessageId());
        assertEquals("tc2", rearranged.get(3).getMessageId());
        assertEquals("tcr2", rearranged.get(4).getMessageId());
    }

    @Test
    void testRearrangeMessagesWithEmptyToolCallId() {
        final var userPrompt = UserPrompt.builder()
                .sessionId(sessionId)
                .runId("r1")
                .messageId("u1")
                .timestamp(1L)
                .content("hi")
                .build();
        final List<AgentMessage> messages = List.of(userPrompt);

        final var rearranged = MessageReadingUtils.rearrangeMessages(messages);
        assertEquals(1, rearranged.size());
        assertEquals("u1", rearranged.get(0).getMessageId());
    }

    @Test
    void testRearrangeMessagesWithMissingResponse() {
        final var tc1 = ToolCall.builder()
                .sessionId(sessionId)
                .runId("r1")
                .messageId("tc1")
                .timestamp(2L)
                .toolCallId("tcid-1")
                .toolName("tool1")
                .build();

        final List<AgentMessage> messages = List.of(tc1);

        final var rearranged = MessageReadingUtils.rearrangeMessages(messages);

        assertTrue(rearranged.isEmpty());
    }
}
