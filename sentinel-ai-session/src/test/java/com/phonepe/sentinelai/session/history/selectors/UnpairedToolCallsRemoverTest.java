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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        final var messages = List.of(new ToolCall(sessionId, runId, reqId, null, "tcX", "tool", "{}"),
                new ToolCallResponse(sessionId, runId, respId, null, "tcX", "tool", null, "r", LocalDateTime.now()));
        final var modifiable = new ArrayList<AgentMessage>(messages);
        final var result = remover.select(sessionId, modifiable);
        final var remainingIds = result.stream().map(AgentMessage::getMessageId).collect(Collectors.toSet());
        assertTrue(remainingIds.contains(reqId));
        assertTrue(remainingIds.contains(respId));
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
        messages.add(new ToolCall(sessionId, runId, firstReqId, null, tcId, "tool", "{}"));
        messages.add(new ToolCall(sessionId, runId, secondReqId, null, tcId, "tool", "{}"));
        final var result = remover.select(sessionId, messages);
        final var remainingIds = result.stream().map(AgentMessage::getMessageId).collect(Collectors.toSet());
        assertFalse(remainingIds.contains(firstReqId));
        assertTrue(remainingIds.contains(secondReqId));
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
        messages.add(new UserPrompt(sessionId, runId, "u", LocalDateTime.now()));
        messages.add(new ToolCall(sessionId, runId, reqOnlyId, null, "tcA", "tool", "{}"));
        messages.add(new ToolCallResponse(sessionId, runId, respOnlyId, null, "tcB", "tool", null, "r", LocalDateTime
                .now()));
        messages.add(new ToolCall(sessionId, runId, pairedReqId, null, "tcC", "tool", "{}"));
        messages.add(new ToolCallResponse(sessionId, runId, pairedRespId, null, "tcC", "tool", null, "r", LocalDateTime
                .now()));
        messages.add(new Text(sessionId, runId, "txt"));
        final var msgIdsByType = messages.stream()
                .collect(Collectors.groupingBy(AgentMessage::getMessageType, Collectors.mapping(
                        AgentMessage::getMessageId, Collectors.toList())));
        final var result = remover.select(sessionId, messages);
        final var remainingIds = result.stream().map(AgentMessage::getMessageId).collect(Collectors.toSet());
        assertFalse(remainingIds.contains(reqOnlyId));
        assertFalse(remainingIds.contains(respOnlyId));
        assertTrue(remainingIds.contains(pairedReqId) || remainingIds.contains(pairedRespId),
                "remainingIds: " + remainingIds);
        assertTrue(remainingIds.contains(msgIdsByType.get(AgentMessageType.TEXT_RESPONSE_MESSAGE).get(0)));
    }
}
