package com.phonepe.sentinelai.core;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OpenAIModel;
import com.phonepe.sentinelai.core.tools.CallableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests out basic functionality for the agent framework
 */
@Slf4j
@WireMockTest
@Disabled
class AgentStructuredOutputTest {

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
        public SimpleAgent(AgentSetup setup, List<AgentExtension> extensions, Map<String, CallableTool> tools) {
            super(OutputObject.class, "greet the user", setup, extensions, tools);
        }

        @Tool("Get name of user")
        public String getName() {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
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
        final var model = new OpenAIModel(
                "gpt-4o",
                OpenAIOkHttpClient.builder()
                        .credential(AzureApiKeyCredential.create("BLAH"))
                        .baseUrl(wiremock.getHttpBaseUrl())
//                        .credential(AzureApiKeyCredential.create(EnvLoader.readEnv("AZURE_API_KEY")))
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
                        .azureServiceVersion(AzureOpenAIServiceVersion.getV2024_10_21())
                        .putAllQueryParams(Map.of("api-version", List.of("2024-10-21")))
                        .jsonMapper(objectMapper)
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
        final var response = agent.execute(new UserInput("Hi"),
                                           requestMetadata,
                                           null,
                                           null);
        log.info("Agent response: {}", response.getData());


        final var response2 = agent.execute(
                new UserInput("What is my name?"),
                requestMetadata,
                response.getAllMessages(),
                null);
        log.info("Second call: {}", response2.getData());
        log.debug("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response2.getAllMessages()));
        assertTrue(response2.getData().message().contains("Santanu"));
    }

}