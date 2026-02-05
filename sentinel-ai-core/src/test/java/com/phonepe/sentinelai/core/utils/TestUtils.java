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

package com.phonepe.sentinelai.core.utils;

import com.github.tomakehurst.wiremock.http.Fault;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@UtilityClass
@Slf4j
public class TestUtils {
    public static <T> void assertNoFailedToolCalls(AgentOutput<T> response) {
        final var messages = response.getNewMessages();
        if (messages == null) {
            return;
        }
        final var failedCall = messages.stream()
                .filter(agentMessage -> agentMessage.getMessageType()
                        .equals(AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE))
                .filter(ToolCallResponse.class::isInstance)
                .map(ToolCallResponse.class::cast)
                .filter(Predicate.not(ToolCallResponse::isSuccess))
                .toList();
        assertTrue(failedCall.isEmpty(), "Expected no failed tool calls, but found: " + failedCall.stream()
                .map(ToolCallResponse::getToolName)
                .collect(java.util.stream.Collectors.joining(", ")));
    }

    public static <T> void ensureOutputGenerated(final AgentOutput<T> response) {
        final var messages = response.getNewMessages();
        assertTrue(messages != null && messages.stream()
                .anyMatch(message -> message.getMessageType()
                        .equals(AgentMessageType.TOOL_CALL_REQUEST_MESSAGE) && message instanceof ToolCall toolCall && toolCall
                                .getToolName()
                                .equals(Agent.OUTPUT_GENERATOR_ID)),
                "Expected at least one output function call, but found none.");
    }

    public static String getTestProperty(String variable, String mockValue) {
        if ("true".equalsIgnoreCase(System.getProperty("sentinelai.useRealEndpoints"))) {
            String value = EnvLoader.readEnv(variable, mockValue);
            log.info("Using real endpoint for {}: {}", variable, value);
            return value;
        }
        log.info("Using mock endpoint for {}: {}", variable, mockValue);
        return mockValue;
    }

    @SneakyThrows
    public static String readStubFile(int i, String prefix, Class<?> clazz) {
        return Files.readString(Path.of(Objects.requireNonNull(clazz.getResource("/wiremock/%s.%d.json".formatted(
                prefix, i))).toURI()));
    }

    public static void setupMocks(int numStates, String prefix, Class<?> clazz) {
        IntStream.rangeClosed(1, numStates).forEach(i -> {
            stubFor(post("/chat/completions?api-version=2024-10-21").inScenario("model-test")
                    .whenScenarioStateIs(i == 1 ? STARTED : Objects.toString(i))
                    .willReturn(okForContentType("application/json", readStubFile(i, prefix, clazz)))
                    .willSetStateTo(Objects.toString(i + 1)));

        });
    }

    public static void setupMocksWithFault(Fault fault) {
        stubFor(post("/chat/completions?api-version=2024-10-21").willReturn(aResponse().withFault(fault)));
    }

    public static void setupMocksWithTimeout(Duration duration) {
        stubFor(post("/chat/completions?api-version=2024-10-21").willReturn(aResponse().withStatus(200)
                .withFixedDelay((int) duration.toMillis())));
    }
}
