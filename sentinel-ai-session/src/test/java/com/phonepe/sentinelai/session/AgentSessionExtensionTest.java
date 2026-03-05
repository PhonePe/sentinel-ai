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
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.events.EventType;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    public record OutputObject(
            String username,
            String message
    ) {
    }

    public record Salutation(
            List<String> salutation
    ) {
    }

    @JsonClassDescription("Parameter to be passed to get salutation for a user")
    public record SalutationParams(
            @JsonPropertyDescription("Name of the user") String name
    ) {
    }

    @JsonClassDescription("User input")
    public record UserInput(
            String data
    ) {
    }


    @Test
    @SneakyThrows
    void test(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(7, "se", getClass());
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

    /**
     * Test addMessagePersistencePreFilter method
     */
    @Test
    void testAddMessagePersistencePreFilter() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Default has 2 filters
        assertEquals(2, extension.getHistoryModifiers().size());

        // Add a custom filter
        extension.addMessagePersistencePreFilter(messages -> messages);

        assertEquals(3, extension.getHistoryModifiers().size());
    }

    /**
     * Test addMessagePersistencePreFilters method with list
     */
    @Test
    void testAddMessagePersistencePreFilters() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Default has 2 filters
        assertEquals(2, extension.getHistoryModifiers().size());

        // Add multiple custom filters
        extension.addMessagePersistencePreFilters(List.of(
                                                          messages -> messages,
                                                          messages -> messages.stream().limit(5).toList()
        ));

        assertEquals(4, extension.getHistoryModifiers().size());
    }

    /**
     * Test addMessageSelector method
     */
    @Test
    void testAddMessageSelector() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Default has 1 selector
        assertEquals(1, extension.getMessageSelectors().size());

        // Add a custom selector
        extension.addMessageSelector((sessionId, messages) -> messages);

        assertEquals(2, extension.getMessageSelectors().size());
    }

    /**
     * Test addMessageSelectors method with list
     */
    @Test
    void testAddMessageSelectors() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Default has 1 selector
        assertEquals(1, extension.getMessageSelectors().size());

        // Add multiple custom selectors
        extension.addMessageSelectors(List.of(
                                              (sessionId, messages) -> messages,
                                              (sessionId, messages) -> messages.stream().limit(10).toList()
        ));

        assertEquals(3, extension.getMessageSelectors().size());
    }

    /**
     * Test additionalSystemPrompts() with null sessionId
     */
    @Test
    void testAdditionalSystemPromptsWithNullSessionId() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Create context with null sessionId
        final var contextWithNullMetadata = new AgentRunContext<>(
                                                                  "run-1",
                                                                  new UserInput("test"),
                                                                  null,  // null requestMetadata -> sessionId is null
                                                                  null,
                                                                  List.of(),
                                                                  null,
                                                                  ProcessingMode.DIRECT
        );

        final var result = extension.additionalSystemPrompts(
                                                             new UserInput("test"),
                                                             contextWithNullMetadata,
                                                             null,
                                                             ProcessingMode.DIRECT);
        assertNotNull(result);
        assertTrue(result.getHints().isEmpty());
    }

    /**
     * Test additionalSystemPrompts() with valid sessionId
     */
    @Test
    void testAdditionalSystemPromptsWithValidSessionId() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Create context with valid sessionId
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("valid-session-id")
                .userId("user-1")
                .build();
        final var context = new AgentRunContext<>(
                                                  "run-1",
                                                  new UserInput("test"),
                                                  requestMetadata,
                                                  null,
                                                  List.of(),
                                                  null,
                                                  ProcessingMode.DIRECT
        );

        final var result = extension.additionalSystemPrompts(
                                                             new UserInput("test"),
                                                             context,
                                                             null,
                                                             ProcessingMode.DIRECT);
        assertNotNull(result);
        assertEquals(1, result.getHints().size());
        assertTrue(result.getHints().get(0).toString().contains("SESSION"));
    }

    /**
     * Test extension builder with null historyModifiers (should use defaults)
     */
    @Test
    void testBuilderWithNullHistoryModifiers() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .historyModifiers(null)
                .build();

        // Should have default 2 modifiers
        assertEquals(2, extension.getHistoryModifiers().size());
    }

    /**
     * Test extension builder with null mapper (should use default)
     */
    @Test
    void testBuilderWithNullMapper() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(null)  // null mapper
                .build();

        // Should not throw, should use default mapper
        assertNotNull(extension.getMapper());
    }

    /**
     * Test extension builder with null messageSelectors (should use defaults)
     */
    @Test
    void testBuilderWithNullMessageSelectors() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .messageSelectors(null)
                .build();

        // Should have default 1 selector
        assertEquals(1, extension.getMessageSelectors().size());
    }

    /**
     * Test extension builder with null setup (should use default)
     */
    @Test
    void testBuilderWithNullSetup() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .setup(null)  // null setup
                .build();

        // Should use default setup
        assertNotNull(extension.getSetup());
        assertEquals(AgentSessionExtensionSetup.DEFAULT.getMaxSummaryLength(),
                     extension.getSetup().getMaxSummaryLength());
    }

    /**
     * Test compactionTriggeringEvents configuration
     */
    @Test
    void testCompactionTriggeringEventsConfiguration() {
        final var sessionStore = new InMemorySessionStore();
        final var customEvents = Set.of(EventType.MESSAGE_RECEIVED);

        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .setup(AgentSessionExtensionSetup.builder()
                        .compactionTriggeringEvents(customEvents)
                        .build())
                .build();

        assertEquals(customEvents, extension.getCompactionTriggeringEvents());
    }

    /**
     * Test facts() method with empty sessionId
     */
    @Test
    void testFactsWithEmptySessionId() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Create context with empty sessionId
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("")  // empty sessionId
                .userId("user-1")
                .build();
        final var contextWithEmptySessionId = new AgentRunContext<>(
                                                                    "run-1",
                                                                    new UserInput("test"),
                                                                    requestMetadata,
                                                                    null,
                                                                    List.of(),
                                                                    null,
                                                                    ProcessingMode.DIRECT
        );

        final var result = extension.facts(new UserInput("test"), contextWithEmptySessionId, null);
        assertTrue(result.isEmpty());
    }

    /**
     * Test facts() method with existing session
     */
    @Test
    void testFactsWithExistingSession() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Save a session first
        sessionStore.saveSession(SessionSummary.builder()
                .sessionId("existing-session")
                .title("Test Session")
                .summary("This is a test summary")
                .raw("{\"summary\": \"test\"}")
                .updatedAt(System.currentTimeMillis())
                .build());

        // Create context with the session id
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("existing-session")
                .userId("user-1")
                .build();
        final var context = new AgentRunContext<>(
                                                  "run-1",
                                                  new UserInput("test"),
                                                  requestMetadata,
                                                  null,
                                                  List.of(),
                                                  null,
                                                  ProcessingMode.DIRECT
        );

        final var result = extension.facts(new UserInput("test"), context, null);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getFact());
        assertEquals(1, result.get(0).getFact().size());
    }

    /**
     * Test facts() method with null/empty sessionId
     */
    @Test
    void testFactsWithNullSessionId() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Create context with null sessionId (null requestMetadata)
        final var contextWithNullMetadata = new AgentRunContext<>(
                                                                  "run-1",
                                                                  new UserInput("test"),
                                                                  null,  // null requestMetadata -> sessionId is null
                                                                  null,
                                                                  List.of(),
                                                                  null,
                                                                  ProcessingMode.DIRECT
        );

        final var result = extension.facts(new UserInput("test"), contextWithNullMetadata, null);
        assertTrue(result.isEmpty());
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
        final var agentSessionExtension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .mapper(objectMapper)
                .sessionStore(sessionStore)
                .setup(AgentSessionExtensionSetup.builder()
                        .autoSummarizationThresholdPercentage(3)
                        .build())
                .build();
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(1)
                                .build())
                        .build())
                .extensions(List.of(agentSessionExtension))
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
        var messages = sessionStore.readMessages("s1",
                                                 Integer.MAX_VALUE,
                                                 false,
                                                 null,
                                                 QueryDirection.OLDER)
                .getItems();
        // request + get_name + summarization + get name response
        // + get_salutation + get_salutation response + output_generation + structured output
        assertEquals(8,
                     messages.size(),
                     "Actual Messages: " + messages.stream()
                             .sorted(Comparator.comparing(AgentMessage::getTimestamp))
                             .map(AgentMessage::getMessageType)
                             .toList());

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

        final var sessionSummary = agentSessionExtension.forceCompaction("s1").get().orElseThrow();

        assertTrue(sessionSummary.getUpdatedAt() > oldSession.getUpdatedAt());
        assertNotNull(sessionStore.session("s1").orElse(null));
        messages = sessionStore.readMessages("s1",
                                             Integer.MAX_VALUE,
                                             false,
                                             null,
                                             QueryDirection.OLDER)
                .getItems();
        // request + get_name + summarization + get name response
        // + get_salutation + get_salutation response + output_generation + structured output
        // round 2.0 ->
        // request + get_location + get location response + get_weather + get weather response
        // + output generation + structured
        assertEquals(16,
                     messages.size(),
                     "Actual Messages: " + messages.stream()
                             .sorted(Comparator.comparing(AgentMessage::getTimestamp))
                             .map(m -> "%s->%s".formatted(m.getMessageType(), m.getMessageId()))
                             .collect(Collectors.joining("\n")));

    }

    /**
     * Test messages() method with empty sessionId
     */
    @Test
    void testMessagesWithEmptySessionId() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Create context with empty sessionId
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("")  // empty sessionId
                .userId("user-1")
                .build();
        final var contextWithEmptySessionId = new AgentRunContext<>(
                                                                    "run-1",
                                                                    new UserInput("test"),
                                                                    requestMetadata,
                                                                    null,
                                                                    List.of(),
                                                                    null,
                                                                    ProcessingMode.DIRECT
        );

        final var result = extension.messages(contextWithEmptySessionId, null, new UserInput("test"));
        assertTrue(result.isEmpty());
    }

    /**
     * Test messages() method with no messages in session
     */
    @Test
    void testMessagesWithNoMessagesInSession() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Create context with a valid sessionId but no messages saved
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("empty-session")
                .userId("user-1")
                .build();
        final var context = new AgentRunContext<>(
                                                  "run-1",
                                                  new UserInput("test"),
                                                  requestMetadata,
                                                  null,
                                                  List.of(),
                                                  null,
                                                  ProcessingMode.DIRECT
        );

        final var result = extension.messages(context, null, new UserInput("test"));
        assertTrue(result.isEmpty());
    }

    /**
     * Test messages() method with null sessionId
     */
    @Test
    void testMessagesWithNullSessionId() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Create context with null sessionId
        final var contextWithNullMetadata = new AgentRunContext<>(
                                                                  "run-1",
                                                                  new UserInput("test"),
                                                                  null,  // null requestMetadata -> sessionId is null
                                                                  null,
                                                                  List.of(),
                                                                  null,
                                                                  ProcessingMode.DIRECT
        );

        final var result = extension.messages(contextWithNullMetadata, null, new UserInput("test"));
        assertTrue(result.isEmpty());
    }

    /**
     * Test name() method
     */
    @Test
    void testName() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        assertEquals("agent-session", extension.name());
    }

    /**
     * Test onSessionSummarized() signal accessor
     */
    @Test
    void testOnSessionSummarizedSignal() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        final var signal = extension.onSessionSummarized();
        assertNotNull(signal);
    }

    /**
     * Test outputSchema() returns empty
     */
    @Test
    void testOutputSchemaReturnsEmpty() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        assertTrue(extension.outputSchema(ProcessingMode.DIRECT).isEmpty());
        assertTrue(extension.outputSchema(ProcessingMode.STREAMING).isEmpty());
    }

    /**
     * Test resetMessagePersistencePreFilters method
     */
    @Test
    void testResetMessagePersistencePreFilters() {
        final var sessionStore = new InMemorySessionStore();
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(JsonUtils.createMapper())
                .build();

        // Default has 2 filters
        assertEquals(2, extension.getHistoryModifiers().size());

        // Reset filters
        extension.resetMessagePersistencePreFilters();

        assertEquals(0, extension.getHistoryModifiers().size());
    }

    /**
     * Test saveMessages with filter that removes all messages
     */
    @Test
    @SneakyThrows
    void testSaveMessagesEmptyAfterModifiers(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(7, "se", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var model = new SimpleOpenAIModel<>(
                                                  "gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils.getTestProperty("AZURE_ENDPOINT",
                                                                                             wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils.getTestProperty("AZURE_API_KEY", "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(objectMapper)
                                                          .clientAdapter(new OkHttpClientAdapter(new OkHttpClient.Builder()
                                                                  .build()))
                                                          .build(),
                                                  objectMapper);

        final var sessionStore = new InMemorySessionStore();
        // Create extension with a filter that removes ALL messages
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(objectMapper)
                .historyModifiers(List.of(
                                          messages -> List.of()  // Filter that removes all messages
                ))
                .build();

        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .build())
                .extensions(List.of(extension))
                .build();

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s-filter-test")
                .userId("test-user")
                .build();

        // Execute the agent - messages will be filtered out before saving
        agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hello"))
                .requestMetadata(requestMetadata)
                .build());

        // Verify no messages were saved due to the filter
        final var messages = sessionStore.readMessages(
                                                       "s-filter-test",
                                                       Integer.MAX_VALUE,
                                                       false,
                                                       null,
                                                       QueryDirection.OLDER);
        assertTrue(messages.getItems().isEmpty());
    }
}
