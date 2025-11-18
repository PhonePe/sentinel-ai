package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelOutput;
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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testEarlyTerminationStrategy(final WireMockRuntimeInfo wiremock) {
        final var terminationInvoked = new AtomicBoolean(false);
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings, modelRunContext, output) -> {
            terminationInvoked.set(true);
            // Return empty Optional to allow normal flow to continue
            return java.util.Optional.empty();
        };

        testInternalWithTerminationStrategy(wiremock,
                     3,
                     "structured-output",
                     setup -> setup.outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT),
                     earlyTerminationStrategy);
        assertTrue(terminationInvoked.get(), "Early termination strategy should have been invoked");
    }

    @Test
    @SneakyThrows
    void testEarlyTerminationStrategyWithModelOutputError(final WireMockRuntimeInfo wiremock) {
        final var forcedTerminationOutput = new AtomicBoolean(false);
        // Strategy that forces early termination
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings, modelRunContext, output) -> {
            forcedTerminationOutput.set(true);
            return Optional.of(ModelOutput.error(List.of(), modelRunContext.getModelUsageStats(), SentinelError.error(ErrorType.MODEL_RUN_TERMINATED)));
        };

        testInternalWithTerminationStrategy(wiremock,
                     3,
                     "structured-output",
                     setup -> setup.outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT),
                     earlyTerminationStrategy);
        assertTrue(forcedTerminationOutput.get(), "Early termination strategy should have forced termination");
    }

    @Test
    @SneakyThrows
    void testEarlyTerminationStrategyWithModelOutputSuccess(final WireMockRuntimeInfo wiremock) {
        final var forcedTerminationOutput = new AtomicBoolean(false);
        // Strategy that forces early termination
        final var earlyTerminationStrategy = (EarlyTerminationStrategy) (modelSettings, modelRunContext, output) -> {
            forcedTerminationOutput.set(true);
            return Optional.of(ModelOutput.success(JsonUtils.createMapper().createObjectNode().set("output",JsonUtils.createMapper().createObjectNode().put("username", "TerminatedUser").put("message", "This run was terminated early.")), List.of(), List.of(), modelRunContext.getModelUsageStats()));
        };

        testInternalWithTerminationStrategy(wiremock,
                3,
                "structured-output",
                setup -> setup.outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT),
                earlyTerminationStrategy);
        assertTrue(forcedTerminationOutput.get(), "Early termination strategy should have forced termination");
    }

    @SneakyThrows
    void testInternal(
            final WireMockRuntimeInfo wiremock,
            final int numStubs,
            final String stubFilePrefix,
            final UnaryOperator<AgentSetup.AgentSetupBuilder> agentSetupUpdater) {
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
    void testInternalWithTerminationStrategy(
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
    }
}

