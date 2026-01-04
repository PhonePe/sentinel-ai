package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
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
import com.phonepe.sentinelai.core.utils.TestUtils;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.cleverclient.retry.RetryConfig;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests {@link SimpleOpenAIModel}
 */
@Slf4j
@WireMockTest
class SimpleOpenAIModelTest {
    public record OutputObject(String username, String message) {
    }

    @JsonClassDescription("Parameter to be passed to get salutation for a user")
    public record SalutationParams(@JsonPropertyDescription("Name of the user") String name) {
    }

    @JsonClassDescription("User input")
    public record UserInput(String data) {
    }

    public record Salutation(List<String> salutation) {
    }

    public static class SimpleAgent extends Agent<UserInput, OutputObject, SimpleAgent> {
        @Builder
        public SimpleAgent(
                AgentSetup setup,
                List<AgentExtension<UserInput, OutputObject, SimpleAgent>> extensions,
                Map<String, ExecutableTool> tools,
                EarlyTerminationStrategy earlyTerminationStrategy) {
            super(OutputObject.class, "greet the user", setup, extensions, tools, null, null, null, earlyTerminationStrategy);
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
    void testNewMessagesAreAddedByThePreprocessor(final WireMockRuntimeInfo wiremock) {
        AtomicInteger iter = new AtomicInteger(0);
         var response = testInternal(wiremock,
                4,
                "tool-output",
                List.of((ctx, allMessages, newMessages) -> {

                    final var processedMessages = new ArrayList<>(allMessages);
                    processedMessages.add(new GenericText(AgentGenericMessage.Role.ASSISTANT,"123-" + iter.getAndIncrement()));

                    return new AgentMessagesPreProcessResult(processedMessages, List.of());
                })
         );
         assertEquals(2, iter.get());
         assertEquals(2, response.getAllMessages().stream().filter(x -> x.getMessageType().equals(AgentMessageType.GENERIC_TEXT_MESSAGE))
                 .map(AgentGenericMessage.class::cast)
                 .filter(x -> x.getRole().equals(AgentGenericMessage.Role.ASSISTANT))
                 .count());
    }

    @Test
    @SneakyThrows
    void testAbsenceOfSystemPromptInPreprocessorOutput(final WireMockRuntimeInfo wiremock) {
        var response = testInternal(wiremock,
                4,
                "tool-output",
                List.of((ctx, allMessages, newMessages) -> {

                    List<AgentMessage> transformedMessages = List.of(
                            new GenericText(AgentGenericMessage.Role.ASSISTANT,"123-"));

                    return new AgentMessagesPreProcessResult(transformedMessages, List.of());
                })
        );
        assertEquals(ErrorType.PREPROCESSOR_MESSAGES_OUTPUT_INVALID, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testAbsenceOfUserMessageInPreprocessorOutput(final WireMockRuntimeInfo wiremock) {
        var response = testInternal(wiremock,
                4,
                "tool-output",
                List.of((ctx, allMessages, newMessages)
                        -> new AgentMessagesPreProcessResult(List.of(allMessages.get(0)), List.of()))
        );
        assertEquals(ErrorType.PREPROCESSOR_MESSAGES_OUTPUT_INVALID, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testNoopPreProcessor(final WireMockRuntimeInfo wiremock) {
        var response = testInternal(wiremock,
                4,
                "tool-output",
                List.of((ctx, allMessages, newMessages)
                        -> new AgentMessagesPreProcessResult(null, null))
        );
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());

        // system prompt + user message + 4 tool calls req/resp + structured output
        assertEquals(2 + 4 + 1, response.getAllMessages().size());
        assertFalse(response.getNewMessages().isEmpty());
    }

    @Test
    @SneakyThrows
    void testEmptyListReturnedByAProcessorFails(final WireMockRuntimeInfo wiremock) {
        var response = testInternal(wiremock,
                4,
                "tool-output",
                List.of((ctx, allMessages, newMessages)
                        -> new AgentMessagesPreProcessResult(List.of(), null))
        );
        assertEquals(ErrorType.PREPROCESSOR_MESSAGES_OUTPUT_INVALID, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testNewMessagesAreUpdatedByAProcessor(final WireMockRuntimeInfo wiremock) {
        var response = testInternal(wiremock,
                4,
                "tool-output",
                List.of((ctx, allMessages, newMessages)
                        -> new AgentMessagesPreProcessResult(allMessages, List.of(new GenericText(AgentGenericMessage.Role.USER, "TEST"))))
        );
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(1, response.getNewMessages()
                .stream()
                .filter(x -> x.getMessageType().equals(AgentMessageType.GENERIC_TEXT_MESSAGE))
                .map(AgentGenericMessage.class::cast)
                .filter( x -> x.getRole().equals(AgentGenericMessage.Role.USER))
                .map(GenericText.class::cast)
                .filter(x -> x.getText().equals("TEST"))
                .count());
    }

    @Test
    @SneakyThrows
    void testExceptionRaisedByPreprocessor(final WireMockRuntimeInfo wiremock) {
        var response = testInternal(wiremock,
                4,
                "tool-output",
                List.of((ctx, allMessages, newMessages) -> {
                    throw new RuntimeException("Errored");
                })
        );

        assertEquals(ErrorType.PREPROCESSOR_RUN_FAILURE, response.getError().getErrorType());

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
    void testEarlyTerminationStrategyShouldContinue(final WireMockRuntimeInfo wiremock) {
        final var terminationInvoked = new AtomicBoolean(false);
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings, modelRunContext, output) -> {
            terminationInvoked.set(true);
            return EarlyTerminationStrategyResponse.doNotTerminate();
        };

        var response = testInternalWithTerminationStrategy(wiremock,
                     3,
                     "structured-output",
                     setup -> setup.outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT),
                     earlyTerminationStrategy);
        assertTrue(terminationInvoked.get(), "Early termination strategy should have been invoked");
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testEarlyTerminationStrategyWithModelOutputError(final WireMockRuntimeInfo wiremock) {
        final var isStrategyInvoked = new AtomicBoolean(false);
        // Strategy that forces early termination
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings, modelRunContext, output) -> {
            isStrategyInvoked.set(true);
            return EarlyTerminationStrategyResponse.terminate(ErrorType.MODEL_RUN_TERMINATED,
                    "Terminating run early as per strategy");
        };

        var response = testInternalWithTerminationStrategy(wiremock,
                     3,
                     "structured-output",
                     setup -> setup.outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT),
                     earlyTerminationStrategy);
        assertTrue(isStrategyInvoked.get(), "Early termination strategy should have been invoked");
        assertEquals(ErrorType.MODEL_RUN_TERMINATED, response.getError().getErrorType());
        assertEquals("Terminating run early as per strategy", response.getError().getMessage());
    }

    @Test
    @SneakyThrows
    void testEarlyTerminationStrategyReturningNull(final WireMockRuntimeInfo wiremock) {
        final var isStrategyInvoked = new AtomicBoolean(false);
        // Strategy that forces early termination
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings, modelRunContext, output) -> {
            isStrategyInvoked.set(true);
            return null;
        };

        var response = testInternalWithTerminationStrategy(wiremock,
                3,
                "structured-output",
                setup -> setup.outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT),
                earlyTerminationStrategy);
        assertTrue(isStrategyInvoked.get(), "Early termination strategy should have been invoked");
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    @SneakyThrows
    AgentOutput<OutputObject> testInternal(
            final WireMockRuntimeInfo wiremock,
            final int numStubs,
            final String stubFilePrefix,
            final List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
        TestUtils.setupMocks(numStubs, stubFilePrefix, getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var model = setupModel("gpt-4o", wiremock, objectMapper);

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
    void testInternal(
            final WireMockRuntimeInfo wiremock,
            final int numStubs,
            final String stubFilePrefix,
            final UnaryOperator<AgentSetup.AgentSetupBuilder> agentSetupUpdater) {
        TestUtils.setupMocks(numStubs, stubFilePrefix, getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var model = setupModel("gpt-4o", wiremock, objectMapper);
        final var eventBus = new EventBus();
        eventBus.onEvent()
                .connect(event -> {
                    if (log.isDebugEnabled()) {
                        try {
                            log.debug("Event: {}", objectMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(event));
                        }
                        catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

        final var agent = SimpleAgent.builder()
                .setup(agentSetupUpdater.apply(AgentSetup.builder()
                                                       .mapper(objectMapper)
                                                       .model(model)
                                                       .modelSettings(ModelSettings.builder()
                                                                              .temperature(0.1f)
                                                                              .seed(42)
                                                                              .build())
                                                       .eventBus(eventBus))
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


        final var response2 = agent.execute(
                AgentInput.<UserInput>builder()
                        .request(new UserInput("What is my name?"))
                        .requestMetadata(requestMetadata)
                        .oldMessages(response.getAllMessages())
                        .build());
        log.info("Second call: {}", response2.getData());
        if (log.isTraceEnabled()) {
            log.trace("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response2.getAllMessages()));
        }
        assertTrue(response2.getData().message().contains("Santanu"));

    }

    @SneakyThrows
    AgentOutput<SimpleOpenAIModelTest.OutputObject> testInternalWithTerminationStrategy(
            final WireMockRuntimeInfo wiremock,
            final int numStubs,
            final String stubFilePrefix,
            final UnaryOperator<AgentSetup.AgentSetupBuilder> agentSetupUpdater,
            final EarlyTerminationStrategy earlyTerminationStrategy) {
        TestUtils.setupMocks(numStubs, stubFilePrefix, getClass());
        final var objectMapper = JsonUtils.createMapper();

        final var httpClient = new OkHttpClient.Builder()
                .build();
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(httpClient))
                        .build(),
                objectMapper
        );
        final var eventBus = new EventBus();
        eventBus.onEvent()
                .connect(event -> {
                    if (log.isDebugEnabled()) {
                        try {
                            log.debug("Event: {}", objectMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(event));
                        }
                        catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

        final var agent = SimpleAgent.builder()
                .setup(agentSetupUpdater.apply(AgentSetup.builder()
                                                       .mapper(objectMapper)
                                                       .model(model)
                                                       .modelSettings(ModelSettings.builder()
                                                                              .temperature(0.1f)
                                                                              .seed(42)
                                                                              .build())
                                                       .eventBus(eventBus))
                               .build())
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

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateHttpCallFailures")
    void testConnectionRateLimit(
            final int status,
            final String payload,
            final ErrorType expectedErrorType,
            final WireMockRuntimeInfo wiremock) {
        stubFor(post("/chat/completions?api-version=2024-10-21")
                        .willReturn(aResponse()
                                            .withStatus(status)
                                            .withBody(payload)));

        final var response = executeAgent(wiremock);
        assertSame(expectedErrorType,
                   response.getError().getErrorType(),
                   "Expected %s after retries, got: %s".formatted(expectedErrorType, response.getError()));
    }

    @Test
    @SneakyThrows
    void testRetriesOnTimeouts(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocksWithTimeout(Duration.ofSeconds(2));

        final var httpClient = new OkHttpClient.Builder()
                .readTimeout(Duration.ofMillis(100))
                .callTimeout(Duration.ofMillis(100))
                .connectTimeout(Duration.ofMillis(100))
                .writeTimeout(Duration.ofMillis(100))
                .build();

        final var model = setupModel("gpt-4o",
                wiremock, JsonUtils.createMapper(), httpClient);

        final var response = executeAgentWithModel(model);
        assertSame(ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                response.getError().getErrorType(),
                "Expected TIMEOUT after retries, got: " + response.getError());
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateFaults")
    void testRetriesForGenericFailure(final Fault fault, final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocksWithFault(fault);
        final var response = executeAgent(wiremock);
        assertSame(ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                response.getError().getErrorType(),
                "Expected COMMUNICATION_ERROR after retries, got: " + response.getError());
    }

    public static Stream<Arguments> generateHttpCallFailures() {
        return Stream.of(
                Arguments.of(429, "Connection Rate Limit", ErrorType.MODEL_CALL_RATE_LIMIT_EXCEEDED),
                Arguments.of(500, "Internal Server Error", ErrorType.MODEL_CALL_HTTP_FAILURE));
    }

    public static Stream<Arguments> generateFaults() {
        return Stream.of(
                Arguments.of(Fault.CONNECTION_RESET_BY_PEER),
                Arguments.of(Fault.MALFORMED_RESPONSE_CHUNK),
                Arguments.of(Fault.RANDOM_DATA_THEN_CLOSE),
                Arguments.of(Fault.EMPTY_RESPONSE));
    }

    private static AgentOutput<OutputObject> executeAgent(final WireMockRuntimeInfo wiremock) {
        final var mapper = JsonUtils.createMapper();
        final var model = setupModel("gpt-4o-mini-2024-07-18", wiremock, mapper);
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

    private static SimpleOpenAIModel<SimpleOpenAIAzure> setupModel(final String modelName,
                                                                   final WireMockRuntimeInfo wiremock,
                                                                   final JsonMapper mapper) {
        final var httpClient = new OkHttpClient.Builder()
                .build();

        return setupModel(modelName, wiremock, mapper, httpClient);
    }

    private static SimpleOpenAIModel<SimpleOpenAIAzure> setupModel(final String modelName,
                                                                   final WireMockRuntimeInfo wiremock,
                                                                   final JsonMapper mapper,
                                                                   final OkHttpClient okHttpClient) {

        return new SimpleOpenAIModel<>(
                modelName,
                SimpleOpenAIAzure.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(mapper)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .retryConfig(RetryConfig.builder()
                                .maxAttempts(1) // disabling implicit retries by default for tests
                                .build())
                        .build(),
                mapper);
    }
}
