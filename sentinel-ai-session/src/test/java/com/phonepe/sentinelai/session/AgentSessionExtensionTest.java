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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.AutoCompactionSetup;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.TestStubs;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private static final class InMemorySessionStore extends SessionStore {

        private final Map<String, SessionSummary> sessionData = new ConcurrentHashMap<>();
        private final Map<String, List<AgentMessage>> messageData = new ConcurrentHashMap<>();

        public InMemorySessionStore() {
            super(SessionExtraDataOperator.empty());
        }

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
            // For OLDER direction: return the most recent 'count' messages in chronological order
            // (oldest-to-newest), matching the contract expected by readMessagesSinceId which
            // iterates pages from newest to oldest but expects each page's items in chronological order.
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

        @Override
        protected Optional<SessionSummary> saveSessionImpl(SessionSummary sessionSummary) {
            sessionData.put(sessionSummary.getSessionId(), sessionSummary);
            return session(sessionSummary.getSessionId());
        }

    }

    /**
     * Test addMessagePersistencePreFilter / addMessageSelector: singular and plural variants
     * register the given functions on top of the defaults.
     */
    static Stream<Arguments> addModifierScenarios() {
        return Stream.of(Arguments.of("single persistence pre-filter",
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> extension
                                              .addMessagePersistencePreFilter(messages -> messages),
                                      1,
                                      1),
                         Arguments.of("multiple persistence pre-filters",
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> extension
                                              .addMessagePersistencePreFilters(List.of(
                                                                                       messages -> messages,
                                                                                       messages -> messages.stream()
                                                                                               .limit(5).toList())),
                                      1,
                                      1),
                         Arguments.of("single message selector",
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> extension
                                              .addMessageSelector((sessionId, messages) -> messages),
                                      1,
                                      2),
                         Arguments.of("multiple message selectors",
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> extension
                                              .addMessageSelectors(List.of(
                                                                           (sessionId, messages) -> messages,
                                                                           (sessionId, messages) -> messages.stream()
                                                                                   .limit(10).toList())),
                                      1,
                                      3));
    }

    /**
     * Test extension builder with null values: each field falls back to its default.
     */
    static Stream<Arguments> builderNullScenarios() {
        return Stream.of(Arguments.of("null historyModifiers",
                                      (UnaryOperator<AgentSessionExtension.AgentSessionExtensionBuilder<UserInput, String, SimpleAgent>>) builder -> builder
                                              .historyModifiers(null),
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> assertEquals(
                                                                                                                                  2,
                                                                                                                                  extension
                                                                                                                                          .getHistoryModifiers()
                                                                                                                                          .size())),
                         Arguments.of("null mapper",
                                      (UnaryOperator<AgentSessionExtension.AgentSessionExtensionBuilder<UserInput, String, SimpleAgent>>) builder -> builder
                                              .mapper(null),
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> assertNotNull(
                                                                                                                                   extension
                                                                                                                                           .getMapper())),
                         Arguments.of("null messageSelectors",
                                      (UnaryOperator<AgentSessionExtension.AgentSessionExtensionBuilder<UserInput, String, SimpleAgent>>) builder -> builder
                                              .messageSelectors(null),
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> assertEquals(
                                                                                                                                  1,
                                                                                                                                  extension
                                                                                                                                          .getMessageSelectors()
                                                                                                                                          .size())),
                         Arguments.of("null setup",
                                      (UnaryOperator<AgentSessionExtension.AgentSessionExtensionBuilder<UserInput, String, SimpleAgent>>) builder -> builder
                                              .setup(null),
                                      (Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>>) extension -> {
                                          assertNotNull(extension.getSetup());
                                          assertEquals(AgentSessionExtensionSetup.DEFAULT
                                                  .getHistoricalMessageFetchSize(),
                                                       extension.getSetup().getHistoricalMessageFetchSize());
                                      }));
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
        TestStubs.setupMocks(7, "se", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .httpClient(new OkHttpClient.Builder().build())
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("addModifierScenarios")
    void testAddMessageModifier(final String name,
                                final Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>> adder,
                                final int defaultSelectorCount,
                                final int expectedSelectorCount) {
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(new InMemorySessionStore())
                .mapper(JsonUtils.createMapper())
                .build();

        // Defaults: 2 history modifiers + defaultSelectorCount selectors.
        assertEquals(2, extension.getHistoryModifiers().size(), name);
        assertEquals(defaultSelectorCount, extension.getMessageSelectors().size(), name);

        adder.accept(extension);

        // Exactly one list grows; the other one is untouched.
        final var addedSelectors = extension.getMessageSelectors().size() - defaultSelectorCount;
        final var addedModifiers = extension.getHistoryModifiers().size() - 2;
        assertEquals(0, addedModifiers * addedSelectors, name);
        assertEquals(expectedSelectorCount, extension.getMessageSelectors().size(), name);
        assertEquals(2 + defaultSelectorCount + addedModifiers + addedSelectors,
                     extension.getHistoryModifiers().size() + extension.getMessageSelectors().size(),
                     name);
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
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builderNullScenarios")
    void testBuilderWithNullValues(final String name,
                                   final UnaryOperator<AgentSessionExtension.AgentSessionExtensionBuilder<UserInput, String, SimpleAgent>> customizer,
                                   final Consumer<AgentSessionExtension<UserInput, String, SimpleAgent>> assertion) {
        final var builder = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(new InMemorySessionStore());
        final var extension = customizer.apply(builder).build();
        assertion.accept(extension);
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
        sessionStore.saveSessionImpl(SessionSummary.builder()
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

    /**
     * Verifies that forceCompaction() sets lastSummarizedMessageId to the last message ID in the
     * compacted slice, so that subsequent session resumes only load messages after that boundary
     * instead of reloading all history from the beginning.
     */
    @Test
    @SneakyThrows
    void testForceCompactionSetsLastSummarizedMessageId(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(8, "summarize", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .httpClient(new OkHttpClient.Builder().build())
                .build();

        final var sessionStore = new InMemorySessionStore();
        final var agentSessionExtension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .mapper(objectMapper)
                .sessionStore(sessionStore)
                .setup(AgentSessionExtensionSetup.DEFAULT)
                .build();
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(1)
                                .build())
                        .autoCompactionSetup(AutoCompactionSetup.DEFAULT.withCompactionTriggerThresholdPercentage(3))
                        .build())
                .extensions(List.of(agentSessionExtension))
                .build()
                .registerToolbox(toolbox);

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();

        // First turn
        agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi"))
                .requestMetadata(requestMetadata)
                .build());

        // Wait for first-time summarization to complete (lastSummarizedMessageId should be null)
        Awaitility.await()
                .pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofMinutes(1))
                .until(() -> sessionStore.session("s1").isPresent());
        assertNull(sessionStore.session("s1").orElseThrow().getLastSummarizedMessageId(),
                   "First-time summarization must leave lastSummarizedMessageId null");

        // Second turn
        agent.executeAsync(AgentInput.<UserInput>builder()
                .request(new UserInput("How is the weather at user's location?"))
                .requestMetadata(requestMetadata)
                .build()).get();

        // Capture IDs of all messages saved so far
        final var allMessagesBefore = sessionStore.readMessages("s1",
                                                                Integer.MAX_VALUE,
                                                                false,
                                                                null,
                                                                QueryDirection.OLDER)
                .getItems();
        assertFalse(allMessagesBefore.isEmpty());
        final var expectedLastId = allMessagesBefore.stream()
                .max(Comparator.comparing(AgentMessage::getTimestamp))
                .orElseThrow()
                .getMessageId();

        // Force compaction
        final var summaryAfterCompaction = agentSessionExtension.forceCompaction("s1").get().orElseThrow();

        // lastSummarizedMessageId must now be set (not null) so subsequent resumes don't reload everything
        assertNotNull(summaryAfterCompaction.getLastSummarizedMessageId(),
                      "forceCompaction() must set lastSummarizedMessageId to avoid reloading all history on resume");
        assertEquals(expectedLastId,
                     summaryAfterCompaction.getLastSummarizedMessageId(),
                     "lastSummarizedMessageId must equal the last message ID in the compacted slice");

        // Verify messages() would now return an empty (or reduced) set since all stored messages
        // are at or before the new lastSummarizedMessageId boundary
        final var requestMetadataResume = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var resumeContext = new AgentRunContext<>(
                                                        "run-resume",
                                                        new UserInput("resume"),
                                                        requestMetadataResume,
                                                        null,
                                                        List.of(),
                                                        null,
                                                        ProcessingMode.DIRECT
        );
        final var messagesOnResume = agentSessionExtension.messages(resumeContext, agent, new UserInput("resume"));
        assertTrue(messagesOnResume.isEmpty(),
                   "After force compaction all stored messages are behind the boundary; messages() should return empty");
    }

    @Test
    @SneakyThrows
    void testHistoryMode(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(8, "summarize", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .httpClient(new OkHttpClient.Builder().build())
                .build();


        final var sessionStore = new InMemorySessionStore();
        final var agentSessionExtension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .mapper(objectMapper)
                .sessionStore(sessionStore)
                .setup(AgentSessionExtensionSetup.DEFAULT)
                .build();
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(1)
                                .build())
                        .autoCompactionSetup(AutoCompactionSetup.DEFAULT.withCompactionTriggerThresholdPercentage(3))
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

    @Test
    void testNoSummaryWhenSessionMissing(WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(3, "sessionless", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .httpClient(new OkHttpClient.Builder().build())
                .build();


        final var sessionStore = new InMemorySessionStore();
        final var agentSessionExtension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .mapper(objectMapper)
                .sessionStore(sessionStore)
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
                .userId("ss") //No session id provided
                .build();
        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi"))
                .requestMetadata(requestMetadata)
                .build());
        log.info("Agent response: {}", response.getData());
        assertTrue(sessionStore.sessions(Integer.MAX_VALUE, null, QueryDirection.NEWER).getItems().isEmpty(),
                   "Session should not be created when sessionId is missing");
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
     * Verifies that the onSessionSummarized signal is dispatched exactly once after a successful
     * first-time summarization, and that the dispatched SessionSummary has a non-null title.
     */
    @Test
    @SneakyThrows
    void testOnSessionSummarizedSignalDispatched(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(7, "se", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .httpClient(new OkHttpClient.Builder().build())
                .build();

        final var sessionStore = new InMemorySessionStore();
        final var agentSessionExtension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .mapper(objectMapper)
                .sessionStore(sessionStore)
                .setup(AgentSessionExtensionSetup.DEFAULT)
                .build();

        // Subscribe to the signal before the agent runs
        final var dispatchedSummaries = new java.util.concurrent.CopyOnWriteArrayList<SessionSummary>();
        agentSessionExtension.onSessionSummarized().connect(dispatchedSummaries::add);

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

        // Run one turn to trigger first-time summarization
        agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi"))
                .requestMetadata(requestMetadata)
                .build());

        // Wait for the async summarization and signal dispatch to complete
        Awaitility.await()
                .pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofMinutes(1))
                .until(() -> !dispatchedSummaries.isEmpty());

        assertEquals(1,
                     dispatchedSummaries.size(),
                     "onSessionSummarized signal must fire exactly once after first-time summarization");
        final var dispatched = dispatchedSummaries.get(0);
        assertNotNull(dispatched.getTitle(), "Dispatched summary must have a non-null title");
        assertNotNull(dispatched.getSummary(), "Dispatched summary must have a non-null summary");
        assertEquals("s1", dispatched.getSessionId(), "Dispatched summary must carry the correct sessionId");
        // First-time summarization leaves lastSummarizedMessageId null (by design)
        assertNull(dispatched.getLastSummarizedMessageId(),
                   "First-time summarization must leave lastSummarizedMessageId null");
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
        TestStubs.setupMocks(7, "se", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .httpClient(new OkHttpClient.Builder().build())
                .build();

        final var sessionStore = new InMemorySessionStore();
        // Create extension with a filter that removes ALL messages
        final var extension = AgentSessionExtension
                .<UserInput, String, SimpleAgent>builder()
                .sessionStore(sessionStore)
                .mapper(objectMapper)
                .historyModifiers(List.of(messages -> List.of()))  // Filter that removes all messages
                .build();

        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .autoCompactionSetup(AutoCompactionSetup.DEFAULT.withCompactionTriggerThresholdPercentage(3))
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
