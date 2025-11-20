package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.awaitility.Awaitility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


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
        assertEquals(response.getError().getErrorType(), ErrorType.SUCCESS);
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
        assertEquals(response.getError().getErrorType(), ErrorType.SUCCESS);
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

    @Test
    @SneakyThrows
    void testRetriesOnTimeouts(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocksWithFault(Fault.CONNECTION_RESET_BY_PEER);

        final var response = executeAgent(wiremock);
        assertSame(ErrorType.TIMEOUT,
                response.getError().getErrorType(),
                "Expected TIMEOUT after retries, got: " + response.getError());
    }

    @Test
    @SneakyThrows
    void testRetriesForGenericFailure(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocksWithFault(Fault.MALFORMED_RESPONSE_CHUNK);

        final var response = executeAgent(wiremock);
        assertSame(ErrorType.GENERIC_MODEL_CALL_FAILURE,
                response.getError().getErrorType(),
                "Expected GENERIC_MODEL_CALL_FAILURE after retries, got: " + response.getError());
    }

    private static AgentOutput<OutputObject> executeAgent(final WireMockRuntimeInfo wiremock) {
        final var mapper = JsonUtils.createMapper();
        final var model = setupModel("gpt-4o-mini-2024-07-18", wiremock, mapper);
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

    @NotNull
    private static SimpleOpenAIModel<SimpleOpenAIAzure> setupModel(final String modelName, final WireMockRuntimeInfo wiremock, final JsonMapper mapper) {
        final var httpClient = new OkHttpClient.Builder()
                .build();

        return new SimpleOpenAIModel<>(
                modelName,
                SimpleOpenAIAzure.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(mapper)
                        .clientAdapter(new OkHttpClientAdapter(httpClient))
                        .build(),
                mapper);
    }
}
