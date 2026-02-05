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

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
@Slf4j
@WireMockTest
class AgentSessionExtensionTest {
    public static class SimpleAgent extends Agent<UserInput, String, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup,
                           List<AgentExtension<UserInput, String, SimpleAgent>> extensions,
                           Map<String, ExecutableTool> tools) {
            super(String.class,
                  "greet the user. do not call get salutation without knowing the username{ ",
                  setup,
                  extensions,
                  tools);
        }

        @Tool("Get name of user")
        public String getName() {
            return "Santanu";
        }

        @Tool("Get salutation for user")
        public Salutation getSalutation(AgentRunContext<SalutationParams> context,
                                        @NonNull SalutationParams params) {
            return new Salutation(List.of("Mr", "Dr", "Prof"));
        }

        @Override
        public String name() {
            return "simple-agent";
        }
    }

    /**
     * A toolbox of extra utilities for the agent
     */
    @Value
    public static class TestToolBox implements ToolBox {
        String user;

        @Tool("Get  location for user")
        public String getLocationForUser(@JsonPropertyDescription("Name of user") final String name) {
            return name.equalsIgnoreCase("Santanu") ? "Bangalore" : "unknown";
        }

        @Tool("Get weather today")
        public String getWeatherToday(@JsonPropertyDescription("Name of user") final String location) {
            return location.equalsIgnoreCase("bangalore") ? "Sunny" : "unknown";
        }

        @Override
        public String name() {
            return AgentUtils.id(user);
        }
    }

    private static final class InMemorySessionStore implements SessionStore {

        private final Map<String, SessionSummary> sessionData = new ConcurrentHashMap<>();
        private final Map<String, List<AgentMessage>> messageData = new ConcurrentHashMap<>();

        @Override
        public boolean deleteSession(String sessionId) {
            return sessionData.remove(sessionId) != null;
        }

        @Override
        public BiScrollable<AgentMessage> readMessages(String sessionId,
                                                       int count,
                                                       boolean skipSystemPrompt,
                                                       BiScrollable.DataPointer pointer,
                                                       QueryDirection queryDirection) {
            var messages = messageData.getOrDefault(sessionId, List.of());
            if (queryDirection == QueryDirection.OLDER) {
                // Return newest first (reverse chronological) to match ESSessionStore
                messages = com.google.common.collect.Lists.reverse(messages);
            }
            return new BiScrollable<>(AgentUtils.lastN(messages, count),
                                      new BiScrollable.DataPointer(null, null));
        }

        @Override
        public void saveMessages(String sessionId,
                                 String runId,
                                 List<AgentMessage> messages) {
            messageData.computeIfAbsent(sessionId,
                                        k -> new java.util.ArrayList<>())
                    .addAll(messages);
        }

        @Override
        public Optional<SessionSummary> saveSession(SessionSummary sessionSummary) {
            sessionData.put(sessionSummary.getSessionId(), sessionSummary);
            return session(sessionSummary.getSessionId());
        }

        @Override
        public Optional<SessionSummary> session(String sessionId) {
            return Optional.ofNullable(sessionData.get(sessionId));
        }

        @Override
        public BiScrollable<SessionSummary> sessions(int count,
                                                     String pointer,
                                                     QueryDirection queryDirection) {
            return new BiScrollable<>(List.copyOf(sessionData.values()),
                                      new BiScrollable.DataPointer(null, null));
        }

    }

    public record OutputObject(String username, String message) {
    }

    public record Salutation(List<String> salutation) {
    }

    @JsonClassDescription("Parameter to be passed to get salutation for a user")
    public record SalutationParams(
            @JsonPropertyDescription("Name of the user") String name) {
    }

    @JsonClassDescription("User input")
    public record UserInput(String data) {
    }


    @Test
    @SneakyThrows
    void test(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(6, "se", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(objectMapper)
                                                          .clientAdapter(new OkHttpClientAdapter(new OkHttpClient.Builder()
                                                                  .build()))
                                                          .build(),
                                                  objectMapper);


        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(1)
                                .build())
                        .build())
                .extensions(List.of(AgentSessionExtension
                        .<UserInput, String, SimpleAgent>builder()
                        .sessionStore(new InMemorySessionStore())
                        .mapper(objectMapper)
                        .build()))
                .build()
                .registerToolbox(toolbox);

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi"))
                .requestMetadata(requestMetadata)
                .build());
        log.info("Agent response: {}", response.getData());


        final var response2 = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("How is the weather at user's location?"))
                .requestMetadata(requestMetadata)
                .oldMessages(response.getAllMessages())
                .build());
        log.info("Second call: {}", response2.getData());
        if (log.isTraceEnabled()) {
            log.trace("Messages: {}",
                      objectMapper.writerWithDefaultPrettyPrinter()
                              .writeValueAsString(response2.getAllMessages()));
        }
    }

    @Test
    @SneakyThrows
    void testHistoryMode(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(8, "summarize", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(objectMapper)
                                                          .clientAdapter(new OkHttpClientAdapter(new OkHttpClient.Builder()
                                                                  .build()))
                                                          .build(),
                                                  objectMapper);


        final var sessionStore = new InMemorySessionStore();
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(1)
                                .build())
                        .build())
                .extensions(List.of(AgentSessionExtension
                        .<UserInput, String, SimpleAgent>builder()
                        .mapper(objectMapper)
                        .sessionStore(sessionStore)
                        .setup(AgentSessionExtensionSetup.builder()
                                .autoSummarizationThresholdPercentage(0)
                                .build())
                        .build()))
                .build()
                .registerToolbox(toolbox);

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi"))
                .requestMetadata(requestMetadata)
                .build());
        log.info("Agent response: {}", response.getData());

        Awaitility.await()
                .pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofMinutes(1))
                .until(() -> sessionStore.session("s1").isPresent());
        final var oldSession = sessionStore.session("s1").orElseThrow();
        assertEquals(8,
                     sessionStore.readMessages("s1",
                                               Integer.MAX_VALUE,
                                               false,
                                               null,
                                               QueryDirection.OLDER)
                             .getItems()
                             .size());

        final var response2 = agent.executeAsync(AgentInput.<UserInput>builder()
                .request(new UserInput("How is the weather at user's location?"))
                .requestMetadata(requestMetadata)
                //.oldMessages(response.getAllMessages())
                .build()).get();
        log.info("Second call: {}", response2.getData());
        if (log.isTraceEnabled()) {
            log.trace("Messages: {}",
                      objectMapper.writerWithDefaultPrettyPrinter()
                              .writeValueAsString(response2.getAllMessages()));
        }

        Awaitility.await()
                .pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofMinutes(1))
                .until(() -> sessionStore.session("s1")
                        .map(SessionSummary::getUpdatedAt)
                        .orElse(-1L) > oldSession.getUpdatedAt());
        assertNotNull(sessionStore.session("s1").orElse(null));
        assertEquals(16,
                     sessionStore.readMessages("s1",
                                               Integer.MAX_VALUE,
                                               false,
                                               null,
                                               QueryDirection.OLDER)
                             .getItems()
                             .size());
    }
}
