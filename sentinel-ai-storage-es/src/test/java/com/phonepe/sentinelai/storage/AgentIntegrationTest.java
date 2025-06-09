package com.phonepe.sentinelai.storage;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinel.session.AgentSessionExtension;
import com.phonepe.sentinelai.agentmemory.AgentMemoryExtension;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.embedding.HuggingfaceEmbeddingModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.storage.memory.ESAgentMemoryStorage;
import com.phonepe.sentinelai.storage.session.ESSessionStore;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for memory extension with persistence
 */
@Slf4j
@WireMockTest
public class AgentIntegrationTest extends ESIntegrationTestBase {
    public record OutputObject(String username, String message) {
    }

    @JsonClassDescription("Parameter to be passed to get salutation for a user")
    public record SalutationParams(@JsonPropertyDescription("Name of the user") @JsonProperty(required = true) String name) {
    }

    @JsonClassDescription("User input")
    public record UserInput(String data) {
    }

    public record Salutation(List<String> salutation) {
    }

    public static class SimpleAgent extends Agent<UserInput, OutputObject, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(OutputObject.class,
                  "greet the user. extract memories about the user to make conversations easier in the future.",
                  setup,
                  extensions,
                  tools);
        }

        @Tool("Get name of user")
        public String getName() {
            return "Santanu";
        }

        @Tool("Get salutation for user")
        public Salutation getSalutation(
                AgentRunContext<SalutationParams> context,
                @NonNull SalutationParams params) {
            return new Salutation(List.of("Mr", "Dr", "Prof"));
        }

        @Override
        public String name() {
            return "simple-agent";
        }
    }

    @Test
    @SneakyThrows
    void test(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(7, "me", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");

        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(new OkHttpClient.Builder()
                                                                       .callTimeout(Duration.ofSeconds(180))
                                                                       .connectTimeout(Duration.ofSeconds(120))
                                                                       .readTimeout(Duration.ofSeconds(180))
                                                                       .writeTimeout(Duration.ofSeconds(120))
                                                                       .build()))
                        .build(),
                objectMapper
        );
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var client = ESClient.builder()
                .serverUrl(ELASTICSEARCH_CONTAINER.getHttpHostAddress())
                .apiKey("test")
                .build();

        final var memoryStorage = new ESAgentMemoryStorage(client, new HuggingfaceEmbeddingModel(), indexPrefix(this));
        final var sessionStorage = new ESSessionStore(client, indexPrefix(this), IndexSettings.DEFAULT);
        final var extensions = List.of(AgentMemoryExtension.builder()
                                               .objectMapper(objectMapper)
                                               .memoryStore(memoryStorage)
                                               .saveMemoryAfterSessionEnd(true)
                                               .build(),
                                       AgentSessionExtension.builder()
                                               .sessionStore(sessionStorage)
                                               .updateSummaryAfterSession(true)
                                               .build());
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(objectMapper)
                               .model(model)
                               .modelSettings(ModelSettings.builder()
                                                      .temperature(0f)
                                                      .seed(0)
                                                      .parallelToolCalls(false)
                                                      .build())
                               .build())
                .extensions(extensions)
                .build()
                .registerToolbox(toolbox);
        {
            final var response = agent.execute(AgentInput.<UserInput>builder()
                                                       .request(new UserInput("Hi"))
                                                       .requestMetadata(requestMetadata)
                                                       .build());
            log.debug("Agent response: {}", response.getData().message());
        }
        {
            final var response = agent.execute(AgentInput.<UserInput>builder()
                                                       .request(new UserInput("Today is sunny in bangalore"))
                                                       .requestMetadata(requestMetadata)
                                                       .build());
            log.debug("Agent response: {}", response.getData().message());
        }
        {
            final var response2 = agent.execute(
                    AgentInput.<UserInput>builder()
                            .request(new UserInput("How is the weather here?"))
                            .requestMetadata(requestMetadata)
                            .build());
            log.info("Second call: {}", response2.getData());
            if (log.isTraceEnabled()) {
                log.trace("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(response2.getAllMessages()));
            }
            assertTrue(response2.getData().message().contains("sunny"));
        }
        final var mems = memoryStorage.findMemoriesAboutUser("ss", null, 5);
        log.info("Memories: {}", mems);
        assertFalse(mems.isEmpty());
        final var session = sessionStorage.session("s1");
        log.info("Session: {}", session);
        assertTrue(session.isPresent());
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
    }
}
