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

package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.Fault;
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
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.RetrySetup;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessResult;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests {@link ConfiguredModel} orchestration. Port of the old
 * {@code SimpleOpenAIModelTest}; wire fixtures are identical, the endpoint is plain
 * {@code /chat/completions} instead of the Azure dialect.
 */
@Slf4j
@WireMockTest
class ConfiguredModelTest {

    public static class SimpleAgent extends Agent<UserInput, OutputObject, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup,
                           List<AgentExtension<UserInput, OutputObject, SimpleAgent>> extensions,
                           Map<String, ExecutableTool> tools,
                           EarlyTerminationStrategy earlyTerminationStrategy) {
            super(OutputObject.class,
                  "greet the user",
                  setup,
                  extensions,
                  tools,
                  null,
                  null,
                  null,
                  earlyTerminationStrategy);
        }

        @Tool("Get name of user")
        public String getName() {
            final var endTime = System.currentTimeMillis() + 1000;
            Awaitility.await()
                    .pollDelay(Duration.ofSeconds(1))
                    .until(() -> System.currentTimeMillis() >= endTime);
            return "Santanu";
        }

        @Override
        public String name() {
            return "simple-agent";
        }
    }

    public static Stream<Arguments> generateFaults() {
        return Stream.of(Arguments.of(Fault.CONNECTION_RESET_BY_PEER),
                         Arguments.of(Fault.MALFORMED_RESPONSE_CHUNK),
                         Arguments.of(Fault.RANDOM_DATA_THEN_CLOSE),
                         Arguments.of(Fault.EMPTY_RESPONSE));
    }

    public static Stream<Arguments> generateHttpCallFailures() {
        return Stream.of(Arguments.of(429,
                                      "Connection Rate Limit",
                                      ErrorType.MODEL_CALL_RATE_LIMIT_EXCEEDED),
                         Arguments.of(500,
                                      "Internal Server Error",
                                      ErrorType.MODEL_CALL_HTTP_FAILURE));
    }

    private static AgentOutput<OutputObject> executeAgent(final WireMockRuntimeInfo wiremock) {
        final var mapper = JsonUtils.createMapper();
        final var model = setupModel(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o-mini-2024-07-18"),
                                     wiremock,
                                     mapper);
        return executeAgentWithModel(model);
    }

    private static AgentOutput<OutputObject> executeAgentWithModel(final Model model) {
        final var mapper = JsonUtils.createMapper();
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(mapper)
                        .model(model)
                        .retrySetup(RetrySetup.builder()
                                .totalAttempts(3)
                                .delayAfterFailedAttempt(Duration.ofMillis(50))
                                .build())
                        .build())
                .build();
        return agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi?"))
                .build());
    }

    private static ConfiguredModel setupModel(final String modelName,
                                              final WireMockRuntimeInfo wiremock,
                                              final ObjectMapper mapper) {
        return setupModel(modelName, wiremock, mapper, new OkHttpClient.Builder().build());
    }

    private static ConfiguredModel setupModel(final String modelName,
                                              final WireMockRuntimeInfo wiremock,
                                              final ObjectMapper mapper,
                                              final OkHttpClient okHttpClient) {
        return TestModels.testModel(modelName,
                                    TestStubs.getTestProperty("AZURE_ENDPOINT", wiremock.getHttpBaseUrl()),
                                    okHttpClient);
    }

    public record OutputObject(
            String username,
            String message
    ) {
    }

    @JsonClassDescription("User input")
    public record UserInput(
            String data
    ) {
    }


    @Test
    @SneakyThrows
    void testAbsenceOfSystemPromptInPreprocessorOutput(final WireMockRuntimeInfo wiremock) {
        final var response = testInternal(wiremock,
                                          4,
                                          "tool-output",
                                          List.of((ctx, allMessages, newMessages) -> {
                                              final List<AgentMessage> transformedMessages = List
                                                      .of(new GenericText("s1",
                                                                          "r1",
                                                                          AgentGenericMessage.Role.ASSISTANT,
                                                                          "123-"));

                                              return new AgentMessagesPreProcessResult(transformedMessages,
                                                                                       List.of());
                                          }));
        assertEquals(ErrorType.PREPROCESSOR_MESSAGES_OUTPUT_INVALID,
                     response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testAbsenceOfUserMessageInPreprocessorOutput(final WireMockRuntimeInfo wiremock) {
        final var response = testInternal(wiremock,
                                          4,
                                          "tool-output",
                                          List.of((ctx, allMessages, newMessages) -> new AgentMessagesPreProcessResult(
                                                                                                                       List.of(allMessages
                                                                                                                               .get(0)),
                                                                                                                       List.of())));
        assertEquals(ErrorType.PREPROCESSOR_MESSAGES_OUTPUT_INVALID,
                     response.getError().getErrorType());
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateHttpCallFailures")
    void testConnectionRateLimit(final int status,
                                 final String payload,
                                 final ErrorType expectedErrorType,
                                 final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        stubFor(post(TestStubs.NO_PREFIX_ENDPOINT).willReturn(aResponse().withStatus(status).withBody(payload)));

        final var response = executeAgent(wiremock);
        assertSame(expectedErrorType,
                   response.getError().getErrorType(),
                   "Expected %s after retries, got: %s".formatted(expectedErrorType,
                                                                  response.getError()));
    }

    @Test
    @SneakyThrows
    void testEarlyTerminationStrategyReturningNull(final WireMockRuntimeInfo wiremock) {
        final var isStrategyInvoked = new AtomicBoolean(false);
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings,
                                                                         modelRunContext,
                                                                         output) -> {
            isStrategyInvoked.set(true);
            return null;
        };

        final var response = testInternalWithTerminationStrategy(wiremock,
                                                                 3,
                                                                 "structured-output",
                                                                 setup -> setup
                                                                         .outputGenerationMode(
                                                                                               OutputGenerationMode.STRUCTURED_OUTPUT),
                                                                 earlyTerminationStrategy);
        assertTrue(isStrategyInvoked.get(), "Early termination strategy should have been invoked");
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testEarlyTerminationStrategyShouldContinue(final WireMockRuntimeInfo wiremock) {
        final var terminationInvoked = new AtomicBoolean(false);
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings,
                                                                         modelRunContext,
                                                                         output) -> {
            terminationInvoked.set(true);
            return EarlyTerminationStrategyResponse.doNotTerminate();
        };

        final var response = testInternalWithTerminationStrategy(wiremock,
                                                                 3,
                                                                 "structured-output",
                                                                 setup -> setup
                                                                         .outputGenerationMode(
                                                                                               OutputGenerationMode.STRUCTURED_OUTPUT),
                                                                 earlyTerminationStrategy);
        assertTrue(terminationInvoked.get(), "Early termination strategy should have been invoked");
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testEarlyTerminationStrategyWithModelOutputError(final WireMockRuntimeInfo wiremock) {
        final var isStrategyInvoked = new AtomicBoolean(false);
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings,
                                                                         modelRunContext,
                                                                         output) -> {
            isStrategyInvoked.set(true);
            return EarlyTerminationStrategyResponse.terminate(ErrorType.MODEL_RUN_TERMINATED,
                                                              "Terminating run early as per strategy");
        };

        final var response = testInternalWithTerminationStrategy(wiremock,
                                                                 3,
                                                                 "structured-output",
                                                                 setup -> setup
                                                                         .outputGenerationMode(
                                                                                               OutputGenerationMode.STRUCTURED_OUTPUT),
                                                                 earlyTerminationStrategy);
        assertTrue(isStrategyInvoked.get(), "Early termination strategy should have been invoked");
        assertEquals(ErrorType.MODEL_RUN_TERMINATED, response.getError().getErrorType());
        assertEquals("Terminating run early as per strategy", response.getError().getMessage());
    }

    @Test
    @SneakyThrows
    void testEmptyListReturnedByAProcessorFails(final WireMockRuntimeInfo wiremock) {
        final var response = testInternal(wiremock,
                                          4,
                                          "tool-output",
                                          List.of((ctx, allMessages, newMessages) -> new AgentMessagesPreProcessResult(
                                                                                                                       List.of(),
                                                                                                                       null)));
        assertEquals(ErrorType.PREPROCESSOR_MESSAGES_OUTPUT_INVALID,
                     response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testExceptionRaisedByPreprocessor(final WireMockRuntimeInfo wiremock) {
        final var response = testInternal(wiremock,
                                          4,
                                          "tool-output",
                                          List.of((ctx, allMessages, newMessages) -> {
                                              throw new RuntimeException("Errored");
                                          }));

        assertEquals(ErrorType.PREPROCESSOR_RUN_FAILURE, response.getError().getErrorType());
    }

    @SneakyThrows
    AgentOutput<OutputObject> testInternal(final WireMockRuntimeInfo wiremock,
                                           final int numStubs,
                                           final String stubFilePrefix,
                                           final List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
        TestStubs.setupMocks(numStubs, stubFilePrefix, getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var model = setupModel(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"), wiremock, objectMapper);

        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(42)
                                .build())
                        .build())
                .build();
        agent.registerAgentMessagesPreProcessors(agentMessagesPreProcessors);

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        return agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi?"))
                .requestMetadata(requestMetadata)
                .build());
    }

    @SneakyThrows
    void testInternal(final WireMockRuntimeInfo wiremock,
                      final int numStubs,
                      final String stubFilePrefix,
                      final UnaryOperator<AgentSetup.AgentSetupBuilder> agentSetupUpdater) {
        TestStubs.setupMocks(numStubs, stubFilePrefix, getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var model = setupModel(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"), wiremock, objectMapper);
        final var eventBus = new EventBus();

        final var agent = SimpleAgent.builder()
                .setup(agentSetupUpdater.apply(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(42)
                                .build())
                        .eventBus(eventBus)).build())
                .build();

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi?"))
                .requestMetadata(requestMetadata)
                .build());
        log.info("Agent response: {}", response.getData());

        final var response2 = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("What is my name?"))
                .requestMetadata(requestMetadata)
                .oldMessages(response.getAllMessages())
                .build());
        log.info("Second call: {}", response2.getData());
        assertTrue(response2.getData().message().contains("Santanu"));
    }

    @SneakyThrows
    AgentOutput<OutputObject> testInternalWithTerminationStrategy(final WireMockRuntimeInfo wiremock,
                                                                  final int numStubs,
                                                                  final String stubFilePrefix,
                                                                  final UnaryOperator<AgentSetup.AgentSetupBuilder> agentSetupUpdater,
                                                                  final EarlyTerminationStrategy earlyTerminationStrategy) {
        TestStubs.setupMocks(numStubs, stubFilePrefix, getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var model = setupModel(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"), wiremock, objectMapper);
        final var eventBus = new EventBus();

        final var agent = SimpleAgent.builder()
                .setup(agentSetupUpdater.apply(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(42)
                                .build())
                        .eventBus(eventBus)).build())
                .earlyTerminationStrategy(earlyTerminationStrategy)
                .build();

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi?"))
                .requestMetadata(requestMetadata)
                .build());
        log.info("Agent response: {}", response.getData());
        return response;
    }

    @Test
    @SneakyThrows
    void testNewMessagesAreAddedByThePreprocessor(final WireMockRuntimeInfo wiremock) {
        final var iter = new AtomicInteger(0);
        final var response = testInternal(wiremock,
                                          4,
                                          "tool-output",
                                          List.of((ctx, allMessages, newMessages) -> {
                                              final var processedMessages = new ArrayList<>(allMessages);
                                              processedMessages.add(new GenericText("s1",
                                                                                    "r1",
                                                                                    AgentGenericMessage.Role.ASSISTANT,
                                                                                    "123-" + iter.getAndIncrement()));

                                              return new AgentMessagesPreProcessResult(processedMessages,
                                                                                       List.of());
                                          }));
        assertEquals(2, iter.get());
        assertEquals(2,
                     response.getAllMessages()
                             .stream()
                             .filter(x -> x.getMessageType()
                                     .equals(AgentMessageType.GENERIC_TEXT_MESSAGE))
                             .map(AgentGenericMessage.class::cast)
                             .filter(x -> x.getRole().equals(AgentGenericMessage.Role.ASSISTANT))
                             .count());
    }

    @Test
    @SneakyThrows
    void testNewMessagesAreUpdatedByAProcessor(final WireMockRuntimeInfo wiremock) {
        final var response = testInternal(wiremock,
                                          4,
                                          "tool-output",
                                          List.of((ctx, allMessages, newMessages) -> new AgentMessagesPreProcessResult(
                                                                                                                       allMessages,
                                                                                                                       List.of(new GenericText("s1",
                                                                                                                                               "r1",
                                                                                                                                               AgentGenericMessage.Role.USER,
                                                                                                                                               "TEST")))));
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(1,
                     response.getNewMessages()
                             .stream()
                             .filter(x -> x.getMessageType()
                                     .equals(AgentMessageType.GENERIC_TEXT_MESSAGE))
                             .map(AgentGenericMessage.class::cast)
                             .filter(x -> x.getRole().equals(AgentGenericMessage.Role.USER))
                             .map(GenericText.class::cast)
                             .filter(x -> x.getText().equals("TEST"))
                             .count());
    }

    @Test
    @SneakyThrows
    void testNoopPreProcessor(final WireMockRuntimeInfo wiremock) {
        final var response = testInternal(wiremock,
                                          4,
                                          "tool-output",
                                          List.of((ctx, allMessages, newMessages) -> new AgentMessagesPreProcessResult(
                                                                                                                       null,
                                                                                                                       null)));
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());

        // system prompt + user message + 4 tool calls req/resp + structured output.
        // One extra message vs the old count: the tool-output fixtures end in a
        // __output_generator__ tool call, and its response message is included too.
        if (TestStubs.useRealEndpoints()) {
            assertFalse(response.getNewMessages().isEmpty());
        }
        else {
            assertEquals(2 + 4 + 1, response.getAllMessages().size() - 1);
        }
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateFaults")
    void testRetriesForGenericFailure(final Fault fault, final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocksWithFault(fault);
        final var response = executeAgent(wiremock);
        assertSame(ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                   response.getError().getErrorType(),
                   "Expected COMMUNICATION_ERROR after retries, got: " + response.getError());
    }

    @Test
    @SneakyThrows
    void testRetriesOnTimeouts(final WireMockRuntimeInfo wiremock) {
        assumeTrue(!TestStubs.useRealEndpoints(), "WireMock-only test");
        TestStubs.setupMocksWithTimeout(Duration.ofSeconds(2));

        final var httpClient = new OkHttpClient.Builder().readTimeout(Duration.ofMillis(100))
                .callTimeout(Duration.ofMillis(100))
                .connectTimeout(Duration.ofMillis(100))
                .writeTimeout(Duration.ofMillis(100))
                .build();

        final var model = setupModel(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"),
                                     wiremock,
                                     JsonUtils.createMapper(),
                                     httpClient);

        final var response = executeAgentWithModel(model);
        assertSame(ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                   response.getError().getErrorType(),
                   "Expected TIMEOUT after retries, got: " + response.getError());
    }

    @Test
    @SneakyThrows
    void testStructuredOutput(final WireMockRuntimeInfo wiremock) {
        testInternal(wiremock,
                     3,
                     "structured-output",
                     setup -> setup.outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT));
    }

    @Test
    @SneakyThrows
    void testToolOutput(final WireMockRuntimeInfo wiremock) {
        final var outputToolCalled = new AtomicBoolean(false);
        testInternal(wiremock,
                     4,
                     "tool-output",
                     setup -> setup.outputGenerationTool(output -> {
                         outputToolCalled.set(true);
                         return output;
                     }));
        assertTrue(outputToolCalled.get());
    }

    @Test
    @SneakyThrows
    void testToolsDisabled(final WireMockRuntimeInfo wiremock) {
        TestStubs.setupMocks(1, "no-tools", getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var model = setupModel(TestStubs.getTestProperty("AZURE_MODEL", "gpt-4o"), wiremock, objectMapper);
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                .temperature(0.1f)
                                .seed(42)
                                .disableTools(true)
                                .build())
                        .build())
                .build();

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(AgentInput.<UserInput>builder()
                .request(new UserInput("Hi?"))
                .requestMetadata(requestMetadata)
                .build());
        log.info("Agent response: {}", response.getData());

        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertNotNull(response.getData());
        assertTrue(response.getData().message().contains("Santanu"));
        assertEquals(0,
                     response.getAllMessages()
                             .stream()
                             .filter(m -> m.getMessageType()
                                     .equals(AgentMessageType.TOOL_CALL_REQUEST_MESSAGE))
                             .count());
        assertEquals(0,
                     response.getAllMessages()
                             .stream()
                             .filter(m -> m.getMessageType()
                                     .equals(AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE))
                             .count());
        assertEquals(1, response.getUsage().getRequestsForRun());
        assertTrue(response.getUsage().getTotalTokens() > 1);
    }
}
