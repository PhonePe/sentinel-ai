package com.phonepe.sentinel.session;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@Slf4j
@WireMockTest
class AgentSessionExtensionTest {
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

    private static final class InMemorySessionStore implements SessionStore {
        private final Map<String, SessionSummary> sessionData = new ConcurrentHashMap<>();

        @Override
        public Optional<SessionSummary> session(String sessionId) {
            return Optional.ofNullable(sessionData.get(sessionId));
        }

        @Override
        public List<SessionSummary> sessions(String agentName) {
            return List.copyOf(sessionData.values());
        }

        @Override
        public Optional<SessionSummary> saveSession(String agentName, SessionSummary sessionSummary) {
            sessionData.put(sessionSummary.getSessionId(), sessionSummary);
            return session(sessionSummary.getSessionId());
        }
    }

    public static class SimpleAgent extends Agent<UserInput, String, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(String.class, "greet the user", setup, extensions, tools);
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
        TestUtils.setupMocks(5, "se", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = new SimpleOpenAIModel(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(new OkHttpClient.Builder().build()))
                        .build(),
                objectMapper
        );


        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(objectMapper)
                               .model(model)
                               .modelSettings(ModelSettings.builder()
                                                      .temperature(0.1f)
                                                      .seed(1)
                                                      .build())
                               .build())
                .extensions(List.of(AgentSessionExtension.builder()
                                            .sessionStore(new InMemorySessionStore())
                                            .updateSummaryAfterSession(true)
                                            .mapper(objectMapper)
                                            .build()))
                .build()
                .registerToolbox(toolbox);

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(
                AgentInput.<UserInput>builder()
                        .request(new UserInput("Hi"))
                        .requestMetadata(requestMetadata)
                        .build());
        log.info("Agent response: {}", response.getData());


        final var response2 = agent.execute(
                AgentInput.<UserInput>builder()
                        .request(new UserInput("How is the weather at user's location?"))
                        .requestMetadata(requestMetadata)
                        .oldMessages(response.getAllMessages())
                        .build());
        log.info("Second call: {}", response2.getData());
        if (log.isTraceEnabled()) {
            log.trace("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response2.getAllMessages()));
        }
    }

    /**
     * A toolbox of extra utilities for the agent
     */
    @Value
    public static class TestToolBox implements ToolBox {
        String user;

        @Tool("Get weather today")
        public String getWeatherToday(@JsonPropertyDescription("Name of user") final String location) {
            return location.equalsIgnoreCase("bangalore") ? "Sunny" : "unknown";
        }

        @Tool("Get  location for user")
        public String getLocationForUser(@JsonPropertyDescription("Name of user") final String name) {
            return name.equalsIgnoreCase("Santanu") ? "Bangalore" : "unknown";
        }

        @Override
        public String name() {
            return AgentUtils.id(user);
        }
    }
}