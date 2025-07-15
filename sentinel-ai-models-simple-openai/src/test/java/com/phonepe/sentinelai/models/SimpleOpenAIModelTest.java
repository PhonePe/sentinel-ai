package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelSettings;
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
        public SimpleAgent(AgentSetup setup, List<AgentExtension<UserInput, OutputObject, SimpleAgent>> extensions, Map<String, ExecutableTool> tools) {
            super(OutputObject.class, "greet the user", setup, extensions, tools);
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
    void test(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(3, "structured-output", getClass());
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

        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(objectMapper)
                               .model(model)
                               .modelSettings(ModelSettings.builder()
                                                      .temperature(0.1f)
                                                      .seed(42)
                                                      .build())
                               .eventBus(eventBus)
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
        if(log.isTraceEnabled()) {
            log.trace("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response2.getAllMessages()));
        }
        assertTrue(response2.getData().message().contains("Santanu"));
    }
}