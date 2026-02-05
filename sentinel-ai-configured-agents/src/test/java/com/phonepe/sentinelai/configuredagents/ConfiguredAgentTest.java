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

package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ConfiguredAgent}
 */
@SuppressWarnings("unchecked")
class ConfiguredAgentTest {

    private static Agent<String, String, ? extends RegisterableAgent<?>> createMockAgent() {
        return (Agent<String, String, ? extends RegisterableAgent<?>>) Mockito
                .mock(Agent.class);
    }

    @Test
    void testRootAgentError() throws Exception {
        final var mockAgent = createMockAgent();
        final var mapper = JsonUtils.createMapper();
        final var error = SentinelError.error(ErrorType.NO_RESPONSE,
                                              new RuntimeException("boom"));
        final var rootOutput = AgentOutput.error(List.of(),
                                                 List.of(),
                                                 null,
                                                 error);
        when(mockAgent.executeAsync(any(AgentInput.class))).thenReturn(
                                                                       CompletableFuture
                                                                               .completedFuture(rootOutput));

        final var configured = new ConfiguredAgent(mockAgent);
        final var input = AgentInput.<JsonNode>builder()
                .request(mapper.createObjectNode())
                .agentSetup(AgentSetup.builder().mapper(mapper).build())
                .build();

        final var result = configured.executeAsync(input).get();
        assertNull(result.getData());
        assertNotNull(result.getError());
        assertEquals(ErrorType.NO_RESPONSE, result.getError().getErrorType());
    }

    @Test
    void testRootAgentException() {
        final var mockAgent = createMockAgent();
        when(mockAgent.executeAsync(any(AgentInput.class))).thenThrow(
                                                                      new RuntimeException("boom"));
        final var mapper = JsonUtils.createMapper();
        final var configured = new ConfiguredAgent(mockAgent);
        final var input = AgentInput.<JsonNode>builder()
                .request(mapper.createObjectNode())
                .agentSetup(AgentSetup.builder().mapper(mapper).build())
                .build();

        assertThrows(RuntimeException.class,
                     () -> configured.executeAsync(input));
    }

    @Test
    void testSuccessfulExecution() throws Exception {
        final var mockAgent = createMockAgent();
        final var mapper = JsonUtils.createMapper();
        final var requestJson = mapper.writeValueAsString(Map.of("hello",
                                                                 "world"));
        final var rootOutput = AgentOutput.success(requestJson,
                                                   List.of(),
                                                   List.of(),
                                                   null);
        when(mockAgent.executeAsync(any(AgentInput.class))).thenReturn(
                                                                       CompletableFuture
                                                                               .completedFuture(rootOutput));

        final var configured = new ConfiguredAgent(mockAgent);
        final var input = AgentInput.<JsonNode>builder()
                .request(mapper.readTree(requestJson))
                .agentSetup(AgentSetup.builder().mapper(mapper).build())
                .build();

        final var future = configured.executeAsync(input);
        final var result = future.get();

        assertNotNull(result.getData());
        assertEquals("world", result.getData().get("hello").asText());
        assertEquals(ErrorType.SUCCESS, result.getError().getErrorType());
    }
}
