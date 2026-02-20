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

package com.phonepe.sentinelai.core.compaction;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCompactorTest {

    private final ObjectMapper mapper = JsonUtils.createMapper();

    @Test
    void testToCompactMessageWithAgentGenericMessageThrowsException() {
        final var genericMessage = new AgentGenericMessage(
                                                           "session-1",
                                                           "run-1",
                                                           "msg-1",
                                                           System.currentTimeMillis(),
                                                           null,
                                                           AgentGenericMessage.Role.USER) {
            @Override
            public <T> T accept(
                                com.phonepe.sentinelai.core.agentmessages.AgentGenericMessageVisitor<T> visitor) {
                return null;
            }
        };

        assertThrows(
                     UnsupportedOperationException.class,
                     () -> MessageCompactor.toCompactMessage(List.of(genericMessage), mapper));
    }

    @Test
    void testToCompactMessageWithEmptyList() {
        final var result = MessageCompactor.toCompactMessage(List.of(), mapper);
        assertTrue(result.isArray());
        assertEquals(0, result.size());
    }

    @Test
    void testToCompactMessageWithMultipleMessages() {
        final var stats = new ModelUsageStats();
        final var messages = List.of(
                                     SystemPrompt.builder()
                                             .sessionId("session-1")
                                             .runId("run-1")
                                             .content("System message")
                                             .build(),
                                     UserPrompt.builder()
                                             .sessionId("session-1")
                                             .runId("run-1")
                                             .content("User message")
                                             .build(),
                                     Text.builder()
                                             .sessionId("session-1")
                                             .runId("run-1")
                                             .content("Assistant message")
                                             .stats(stats)
                                             .build());

        final var result = MessageCompactor.toCompactMessage(messages, mapper);

        assertEquals(3, result.size());
        assertEquals(CompactMessage.Roles.SYSTEM, result.get(0).get("role").asText());
        assertEquals(CompactMessage.Roles.USER, result.get(1).get("role").asText());
        assertEquals(CompactMessage.Roles.ASSISTANT, result.get(2).get("role").asText());
    }

    @Test
    void testToCompactMessageWithStructuredOutput() {
        final var structuredOutput = StructuredOutput.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("Here is the structured response")
                .stats(new ModelUsageStats())
                .build();

        final var result = MessageCompactor.toCompactMessage(List.of(structuredOutput), mapper);

        assertEquals(1, result.size());
        final var node = result.get(0);
        assertEquals(CompactMessage.Types.CHAT, node.get("type").asText());
        assertEquals(CompactMessage.Roles.ASSISTANT, node.get("role").asText());
        assertEquals("Here is the structured response", node.get("content").asText());
    }

    @Test
    void testToCompactMessageWithSystemPrompt() {
        final var systemPrompt = SystemPrompt.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("You are a helpful assistant")
                .build();

        final var result = MessageCompactor.toCompactMessage(List.of(systemPrompt), mapper);

        assertEquals(1, result.size());
        final var node = result.get(0);
        assertEquals(CompactMessage.Types.CHAT, node.get("type").asText());
        assertEquals(CompactMessage.Roles.SYSTEM, node.get("role").asText());
        assertEquals("You are a helpful assistant", node.get("content").asText());
    }

    @Test
    void testToCompactMessageWithTextResponse() {
        final var textResponse = Text.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("Here is a text response")
                .stats(new ModelUsageStats())
                .build();

        final var result = MessageCompactor.toCompactMessage(List.of(textResponse), mapper);

        assertEquals(1, result.size());
        final var node = result.get(0);
        assertEquals(CompactMessage.Types.CHAT, node.get("type").asText());
        assertEquals(CompactMessage.Roles.ASSISTANT, node.get("role").asText());
        assertEquals("Here is a text response", node.get("content").asText());
    }

    @Test
    void testToCompactMessageWithToolCall() throws Exception {
        final var toolCall = ToolCall.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-123")
                .toolName("get_weather")
                .arguments("{\"city\": \"London\"}")
                .build();

        final var result = MessageCompactor.toCompactMessage(List.of(toolCall), mapper);

        assertEquals(1, result.size());
        final var node = result.get(0);
        assertEquals(CompactMessage.Types.TOOL_CALL_RESPONSE, node.get("type").asText());
        assertEquals("call-123", node.get("callId").asText());
        assertNotNull(node.get("arguments"));
    }

    @Test
    void testToCompactMessageWithToolCallResponse() {
        final var toolCallResponse = ToolCallResponse.builder()
                .sessionId("session-1")
                .runId("run-1")
                .toolCallId("call-123")
                .toolName("get_weather")
                .response("{\"temperature\": 20}")
                .errorType(ErrorType.SUCCESS)
                .build();

        final var result = MessageCompactor.toCompactMessage(List.of(toolCallResponse), mapper);

        assertEquals(1, result.size());
        final var node = result.get(0);
        assertEquals(CompactMessage.Types.TOOL_CALL_RESPONSE, node.get("type").asText());
        assertEquals("call-123", node.get("callId").asText());
        assertEquals("{\"temperature\": 20}", node.get("result").asText());
    }

    @Test
    void testToCompactMessageWithUserPrompt() {
        final var userPrompt = UserPrompt.builder()
                .sessionId("session-1")
                .runId("run-1")
                .content("What is the weather?")
                .build();

        final var result = MessageCompactor.toCompactMessage(List.of(userPrompt), mapper);

        assertEquals(1, result.size());
        final var node = result.get(0);
        assertEquals(CompactMessage.Types.CHAT, node.get("type").asText());
        assertEquals(CompactMessage.Roles.USER, node.get("role").asText());
        assertEquals("What is the weather?", node.get("content").asText());
    }
}
