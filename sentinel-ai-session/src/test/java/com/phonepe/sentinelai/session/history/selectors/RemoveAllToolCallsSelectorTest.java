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

package com.phonepe.sentinelai.session.history.selectors;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage.Role;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveAllToolCallsSelectorTest {

    @Test
    void testEmptyInputReturnsEmpty() {
        var selector = new RemoveAllToolCallsSelector();
        final var messages = new ArrayList<AgentMessage>();
        final var result = selector.select("s-4", messages);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMultipleSessions() {
        var selector = new RemoveAllToolCallsSelector();
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt("session-1", "run-1", "u1", LocalDateTime.now()),
                                                   new ToolCall("session-1", "run-1", "tc-1", "tool", "{}"),
                                                   new UserPrompt("session-2", "run-2", "u2", LocalDateTime.now()),
                                                   new ToolCall("session-2", "run-2", "tc-2", "tool", "{}")
        );
        final var result = selector.select("session-1", new ArrayList<>(messages));
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(m -> m.getMessageType().name().contains("TOOL_CALL")));
    }

    @Test
    void testNoToolCallsKeepsAllMessages() {
        var selector = new RemoveAllToolCallsSelector();
        final var sessionId = "s-3";
        final var runId = "r-3";
        final var messages = List.of(new UserPrompt(sessionId,
                                                    runId,
                                                    "u3",
                                                    LocalDateTime.now()),
                                     new Text(sessionId,
                                              runId,
                                              "t3",
                                              new ModelUsageStats(),
                                              100),
                                     new GenericText(sessionId,
                                                     runId,
                                                     Role.USER,
                                                     "g3"));
        final var modifiable = new ArrayList<AgentMessage>(messages);
        final var result = selector.select(sessionId, modifiable);
        assertEquals(3, result.size());
    }

    @Test
    void testPreservesNonToolCallMessageOrder() {
        var selector = new RemoveAllToolCallsSelector();
        final var sessionId = "s-6";
        final var runId = "r-6";
        var userPrompt = new UserPrompt(sessionId, runId, "user", LocalDateTime.now());
        var textResponse = new Text(sessionId, runId, "text", new ModelUsageStats(), 100);
        var genericText = new GenericText(sessionId, runId, Role.USER, "generic");
        final var messages = List.<AgentMessage>of(
                                                   userPrompt,
                                                   new ToolCall(sessionId, runId, "tc-1", "tool", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "tc-1",
                                                                        "tool",
                                                                        null,
                                                                        "resp",
                                                                        LocalDateTime.now()),
                                                   textResponse,
                                                   new ToolCall(sessionId, runId, "tc-2", "tool2", "{}"),
                                                   genericText
        );
        final var result = selector.select(sessionId, new ArrayList<>(messages));
        assertEquals(3, result.size());
        assertEquals(userPrompt, result.get(0));
        assertEquals(textResponse, result.get(1));
        assertEquals(genericText, result.get(2));
    }

    @Test
    void testRemoveMultipleToolCallsFromMultipleRuns() {
        var selector = new RemoveAllToolCallsSelector();
        final var sessionId = "s-5";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(sessionId, "run-1", "user1", LocalDateTime.now()),
                                                   new ToolCall(sessionId, "run-1", "tc-1", "tool1", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        "run-1",
                                                                        "tc-1",
                                                                        "tool1",
                                                                        null,
                                                                        "resp1",
                                                                        LocalDateTime.now()),
                                                   new Text(sessionId, "run-1", "text1", new ModelUsageStats(), 100),
                                                   new UserPrompt(sessionId, "run-2", "user2", LocalDateTime.now()),
                                                   new ToolCall(sessionId, "run-2", "tc-2", "tool2", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        "run-2",
                                                                        "tc-2",
                                                                        "tool2",
                                                                        null,
                                                                        "resp2",
                                                                        LocalDateTime.now()),
                                                   new Text(sessionId, "run-2", "text2", new ModelUsageStats(), 100)
        );
        final var result = selector.select(sessionId, new ArrayList<>(messages));
        assertEquals(4, result.size());
        assertTrue(result.stream().noneMatch(m -> m.getMessageType().name().contains("TOOL_CALL")));
    }

    @Test
    void testRemoveToolCallsFromMixedMessages() {
        var selector = new RemoveAllToolCallsSelector();
        final var sessionId = "s-1";
        final var runId = "r-1";
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new UserPrompt(sessionId,
                                    runId,
                                    "u1",
                                    LocalDateTime.now()));
        messages.add(new Text(sessionId,
                              runId,
                              "t1",
                              new ModelUsageStats(),
                              100));
        messages.add(new ToolCall(sessionId, runId, "tc-1", "tool", "{}"));
        messages.add(new ToolCallResponse(sessionId,
                                          runId,
                                          "tc-1",
                                          "tool",
                                          null,
                                          "resp",
                                          LocalDateTime.now()));
        messages.add(new GenericText(sessionId, runId, Role.USER, "g1"));
        final var result = selector.select(sessionId, messages);
        assertEquals(3, result.size());
        assertTrue(result.stream()
                .noneMatch(m -> m.getMessageType()
                        .name()
                        .contains("TOOL_CALL")));
    }

    @Test
    void testRemoveWhenOnlyToolCallsReturnsEmpty() {
        var selector = new RemoveAllToolCallsSelector();
        final var sessionId = "s-2";
        final var runId = "r-2";
        final var messages = List.of(new ToolCall(sessionId,
                                                  runId,
                                                  "tc-2",
                                                  "tool",
                                                  "{}"),
                                     new ToolCallResponse(sessionId,
                                                          runId,
                                                          "tc-2",
                                                          "tool",
                                                          null,
                                                          "resp",
                                                          LocalDateTime.now()));
        final var modifiable = new ArrayList<AgentMessage>(messages);
        final var result = selector.select(sessionId, modifiable);
        assertTrue(result.isEmpty());
    }

    @Test
    void testRemovesOrphanedToolCallRequest() {
        var selector = new RemoveAllToolCallsSelector();
        final var sessionId = "s-7";
        final var runId = "r-7";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(sessionId, runId, "tc-orphan", "tool", "{}"),
                                                   new Text(sessionId, runId, "text", new ModelUsageStats(), 100)
        );
        final var result = selector.select(sessionId, new ArrayList<>(messages));
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(m -> m instanceof ToolCall));
    }

    @Test
    void testRemovesOrphanedToolCallResponse() {
        var selector = new RemoveAllToolCallsSelector();
        final var sessionId = "s-8";
        final var runId = "r-8";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "tc-orphan",
                                                                        "tool",
                                                                        null,
                                                                        "resp",
                                                                        LocalDateTime.now()),
                                                   new Text(sessionId, runId, "text", new ModelUsageStats(), 100)
        );
        final var result = selector.select(sessionId, new ArrayList<>(messages));
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(m -> m instanceof ToolCallResponse));
    }

    @Test
    void testWithNullSessionId() {
        var selector = new RemoveAllToolCallsSelector();
        final var runId = "r-9";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(null, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(null, runId, "tc-1", "tool", "{}"),
                                                   new Text(null, runId, "text", new ModelUsageStats(), 100)
        );
        final var result = selector.select(null, new ArrayList<>(messages));
        assertEquals(2, result.size());
    }
}
