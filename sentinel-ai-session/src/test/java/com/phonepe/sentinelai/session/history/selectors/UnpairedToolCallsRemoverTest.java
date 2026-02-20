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
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnpairedToolCallsRemoverTest {

    @Test
    void testEmptyInputReturnsEmpty() {
        var remover = new UnpairedToolCallsRemover();
        final var messages = new ArrayList<AgentMessage>();
        final var result = remover.select("s4", messages);
        assertTrue(result.isEmpty());
    }

    @Test
    void testLeavesPairedToolCallsIntact() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s2";
        final var runId = "r2";
        final var reqId = "req-p";
        final var respId = "resp-p";
        final var messages = List.of(new ToolCall(sessionId,
                                                  runId,
                                                  reqId,
                                                  null,
                                                  "tcX",
                                                  "tool",
                                                  "{}"),
                                     new ToolCallResponse(sessionId,
                                                          runId,
                                                          respId,
                                                          null,
                                                          "tcX",
                                                          "tool",
                                                          null,
                                                          "r",
                                                          LocalDateTime.now()));
        final var modifiable = new ArrayList<AgentMessage>(messages);
        final var result = remover.select(sessionId, modifiable);
        final var remainingIds = result.stream()
                .map(AgentMessage::getMessageId)
                .collect(Collectors.toSet());
        assertTrue(remainingIds.contains(reqId));
        assertTrue(remainingIds.contains(respId));
    }

    @Test
    void testMixedPairedAndUnpairedWithDifferentToolCallIds() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s11";
        final var runId = "r11";
        final var pairedReqId = "paired-req";
        final var pairedRespId = "paired-resp";
        final var unpairedReqId = "unpaired-req";
        final var unpairedRespId = "unpaired-resp";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(sessionId,
                                                                runId,
                                                                pairedReqId,
                                                                null,
                                                                "tc-paired",
                                                                "tool1",
                                                                "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        pairedRespId,
                                                                        null,
                                                                        "tc-paired",
                                                                        "tool1",
                                                                        null,
                                                                        "ok",
                                                                        LocalDateTime.now()),
                                                   new ToolCall(sessionId,
                                                                runId,
                                                                unpairedReqId,
                                                                null,
                                                                "tc-unpaired-req",
                                                                "tool2",
                                                                "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        unpairedRespId,
                                                                        null,
                                                                        "tc-unpaired-resp",
                                                                        "tool3",
                                                                        null,
                                                                        "orphan",
                                                                        LocalDateTime.now()),
                                                   new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        final var remainingIds = result.stream()
                .map(AgentMessage::getMessageId)
                .collect(Collectors.toSet());
        assertTrue(remainingIds.contains(pairedReqId));
        assertTrue(remainingIds.contains(pairedRespId));
        assertFalse(remainingIds.contains(unpairedReqId));
        assertFalse(remainingIds.contains(unpairedRespId));
        assertEquals(4, result.size());
    }

    @Test
    void testMultiplePairedToolCallsKeepsAll() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s6";
        final var runId = "r6";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(sessionId, runId, "req-1", null, "tc-1", "tool1", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "resp-1",
                                                                        null,
                                                                        "tc-1",
                                                                        "tool1",
                                                                        null,
                                                                        "r1",
                                                                        LocalDateTime.now()),
                                                   new ToolCall(sessionId, runId, "req-2", null, "tc-2", "tool2", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "resp-2",
                                                                        null,
                                                                        "tc-2",
                                                                        "tool2",
                                                                        null,
                                                                        "r2",
                                                                        LocalDateTime.now()),
                                                   new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertEquals(6, result.size());
    }

    @Test
    void testMultipleSessions() {
        var remover = new UnpairedToolCallsRemover();
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt("session-1", "run-1", "u1", LocalDateTime.now()),
                                                   new ToolCall("session-1",
                                                                "run-1",
                                                                "req-1",
                                                                null,
                                                                "tc-1",
                                                                "tool",
                                                                "{}"),
                                                   new ToolCallResponse("session-1",
                                                                        "run-1",
                                                                        "resp-1",
                                                                        null,
                                                                        "tc-1",
                                                                        "tool",
                                                                        null,
                                                                        "r1",
                                                                        LocalDateTime.now()),
                                                   new UserPrompt("session-2", "run-2", "u2", LocalDateTime.now()),
                                                   new ToolCall("session-2",
                                                                "run-2",
                                                                "req-2",
                                                                null,
                                                                "tc-2",
                                                                "tool",
                                                                "{}")
        );
        final var result = remover.select("session-1", new ArrayList<>(messages));
        final var remainingIds = result.stream()
                .map(AgentMessage::getMessageId)
                .collect(Collectors.toSet());
        assertTrue(remainingIds.contains("req-1"));
        assertTrue(remainingIds.contains("resp-1"));
    }

    @Test
    void testMultipleUnpairedRequestsRemovesFirstOccurrenceOnly() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s3";
        final var runId = "r3";
        final var firstReqId = "first-req";
        final var secondReqId = "second-req";
        final var tcId = "tcRepeat";
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new ToolCall(sessionId,
                                  runId,
                                  firstReqId,
                                  null,
                                  tcId,
                                  "tool",
                                  "{}"));
        messages.add(new ToolCall(sessionId,
                                  runId,
                                  secondReqId,
                                  null,
                                  tcId,
                                  "tool",
                                  "{}"));
        final var result = remover.select(sessionId, messages);
        final var remainingIds = result.stream()
                .map(AgentMessage::getMessageId)
                .collect(Collectors.toSet());
        assertFalse(remainingIds.contains(firstReqId));
        assertTrue(remainingIds.contains(secondReqId));
    }

    @Test
    void testNoToolCallsKeepsAllMessages() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s5";
        final var runId = "r5";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertEquals(2, result.size());
    }

    @Test
    void testOnlyUnpairedRequestsRemovesAll() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s7";
        final var runId = "r7";
        final var messages = List.<AgentMessage>of(
                                                   new ToolCall(sessionId, runId, "req-1", null, "tc-1", "tool1", "{}"),
                                                   new ToolCall(sessionId, runId, "req-2", null, "tc-2", "tool2", "{}"),
                                                   new ToolCall(sessionId, runId, "req-3", null, "tc-3", "tool3", "{}")
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertTrue(result.isEmpty());
    }

    @Test
    void testOnlyUnpairedResponsesRemovesAll() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s8";
        final var runId = "r8";
        final var messages = List.<AgentMessage>of(
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "resp-1",
                                                                        null,
                                                                        "tc-1",
                                                                        "tool1",
                                                                        null,
                                                                        "r1",
                                                                        LocalDateTime.now()),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "resp-2",
                                                                        null,
                                                                        "tc-2",
                                                                        "tool2",
                                                                        null,
                                                                        "r2",
                                                                        LocalDateTime.now())
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertTrue(result.isEmpty());
    }

    @Test
    void testPreservesNonToolCallMessageOrder() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s9";
        final var runId = "r9";
        var userPrompt = new UserPrompt(sessionId, runId, "user", LocalDateTime.now());
        var textResponse = new Text(sessionId, runId, "response", new ModelUsageStats(), 100);
        final var messages = List.<AgentMessage>of(
                                                   userPrompt,
                                                   new ToolCall(sessionId,
                                                                runId,
                                                                "req-1",
                                                                null,
                                                                "tc-unpaired",
                                                                "tool",
                                                                "{}"),
                                                   textResponse
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertEquals(2, result.size());
        assertEquals(userPrompt, result.get(0));
        assertEquals(textResponse, result.get(1));
    }

    @Test
    void testRemovesUnpairedRequestsAndResponses() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s1";
        final var runId = "r1";
        final var reqOnlyId = "req-only-msg";
        final var respOnlyId = "resp-only-msg";
        final var pairedReqId = "paired-req-msg";
        final var pairedRespId = "paired-resp-msg";
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new UserPrompt(sessionId,
                                    runId,
                                    "u",
                                    LocalDateTime.now()));
        messages.add(new ToolCall(sessionId,
                                  runId,
                                  reqOnlyId,
                                  null,
                                  "tcA",
                                  "tool",
                                  "{}"));
        messages.add(new ToolCallResponse(sessionId,
                                          runId,
                                          respOnlyId,
                                          null,
                                          "tcB",
                                          "tool",
                                          null,
                                          "r",
                                          LocalDateTime.now()));
        messages.add(new ToolCall(sessionId,
                                  runId,
                                  pairedReqId,
                                  null,
                                  "tcC",
                                  "tool",
                                  "{}"));
        messages.add(new ToolCallResponse(sessionId,
                                          runId,
                                          pairedRespId,
                                          null,
                                          "tcC",
                                          "tool",
                                          null,
                                          "r",
                                          LocalDateTime.now()));
        messages.add(new Text(sessionId,
                              runId,
                              "txt",
                              new ModelUsageStats(),
                              100));
        final var msgIdsByType = messages.stream()
                .collect(Collectors.groupingBy(AgentMessage::getMessageType,
                                               Collectors.mapping(
                                                                  AgentMessage::getMessageId,
                                                                  Collectors
                                                                          .toList())));
        final var result = remover.select(sessionId, messages);
        final var remainingIds = result.stream()
                .map(AgentMessage::getMessageId)
                .collect(Collectors.toSet());
        assertFalse(remainingIds.contains(reqOnlyId));
        assertFalse(remainingIds.contains(respOnlyId));
        assertTrue(remainingIds.contains(pairedReqId) || remainingIds.contains(
                                                                               pairedRespId),
                   "remainingIds: " + remainingIds);
        assertTrue(remainingIds.contains(msgIdsByType.get(
                                                          AgentMessageType.TEXT_RESPONSE_MESSAGE)
                .get(0)));
    }

    @Test
    void testWithGenericTextMessages() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s13";
        final var runId = "r13";
        final var messages = List.<AgentMessage>of(
                                                   new GenericText(sessionId, runId, Role.USER, "generic text"),
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(sessionId, runId, "req-1", null, "tc-1", "tool", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "resp-1",
                                                                        null,
                                                                        "tc-1",
                                                                        "tool",
                                                                        null,
                                                                        "r1",
                                                                        LocalDateTime.now()),
                                                   new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertEquals(5, result.size());
    }

    @Test
    void testWithMixedMessageTypesAndUnpairedToolCall() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s15";
        final var runId = "r15";
        final var messages = List.<AgentMessage>of(
                                                   new SystemPrompt(sessionId, runId, "system", true, "m"),
                                                   new GenericText(sessionId, runId, Role.USER, "generic"),
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(sessionId,
                                                                runId,
                                                                "req-1",
                                                                null,
                                                                "tc-unpaired",
                                                                "tool",
                                                                "{}"),
                                                   new Text(sessionId, runId, "text", new ModelUsageStats(), 100),
                                                   new StructuredOutput(sessionId,
                                                                        runId,
                                                                        "{}",
                                                                        new ModelUsageStats(),
                                                                        100)
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        final var remainingIds = result.stream()
                .map(AgentMessage::getMessageId)
                .collect(Collectors.toSet());
        assertEquals(5, result.size());
        assertFalse(remainingIds.contains("req-1"));
    }

    @Test
    void testWithNullSessionId() {
        var remover = new UnpairedToolCallsRemover();
        final var runId = "r10";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(null, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(null, runId, "req-1", null, "tc-1", "tool", "{}"),
                                                   new ToolCallResponse(null,
                                                                        runId,
                                                                        "resp-1",
                                                                        null,
                                                                        "tc-1",
                                                                        "tool",
                                                                        null,
                                                                        "r1",
                                                                        LocalDateTime.now())
        );
        final var result = remover.select(null, new ArrayList<>(messages));
        assertEquals(3, result.size());
    }

    @Test
    void testWithStructuredOutputResponse() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s14";
        final var runId = "r14";
        final var messages = List.<AgentMessage>of(
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(sessionId, runId, "req-1", null, "tc-1", "tool", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "resp-1",
                                                                        null,
                                                                        "tc-1",
                                                                        "tool",
                                                                        null,
                                                                        "r1",
                                                                        LocalDateTime.now()),
                                                   new StructuredOutput(sessionId,
                                                                        runId,
                                                                        "{\"key\": \"value\"}",
                                                                        new ModelUsageStats(),
                                                                        100)
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertEquals(4, result.size());
    }

    @Test
    void testWithSystemPromptMessages() {
        var remover = new UnpairedToolCallsRemover();
        final var sessionId = "s12";
        final var runId = "r12";
        final var messages = List.<AgentMessage>of(
                                                   new SystemPrompt(sessionId, runId, "system prompt", true, "method"),
                                                   new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                                   new ToolCall(sessionId, runId, "req-1", null, "tc-1", "tool", "{}"),
                                                   new ToolCallResponse(sessionId,
                                                                        runId,
                                                                        "resp-1",
                                                                        null,
                                                                        "tc-1",
                                                                        "tool",
                                                                        null,
                                                                        "r1",
                                                                        LocalDateTime.now()),
                                                   new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        final var result = remover.select(sessionId, new ArrayList<>(messages));
        assertEquals(5, result.size());
    }
}
