package com.phonepe.sentinel.session;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OpenAIModel;
import com.phonepe.sentinelai.core.tools.CallableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@Slf4j
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
        public Optional<SessionSummary> saveSession(SessionSummary sessionSummary) {
            sessionData.put(sessionSummary.getSessionId(), sessionSummary);
            return session(sessionSummary.getSessionId());
        }
    }

    //    public static class SimpleAgent extends Agent<UserInput, Void, OutputObject, SimpleAgent> {
    public static class SimpleAgent extends Agent<UserInput, Void, String, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup, Map<String, CallableTool> tools) {
            super(String.class, "greet the user", setup, tools);
        }

        @Tool("Get name of user")
        public String getName() {
            return "Santanu";
        }

        @Tool("Get salutation for user")
        public Salutation getSalutation(
                AgentRunContext<Void, SalutationParams> context,
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
    void test() {
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var model = new OpenAIModel(
                "gpt-4o",
                OpenAIOkHttpClient.builder()
                        .credential(AzureApiKeyCredential.create(EnvLoader.readEnv("AZURE_API_KEY")))
                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
                        .azureServiceVersion(AzureOpenAIServiceVersion.getV2024_10_21())
                        .putAllQueryParams(Map.of("api-version", List.of("2024-10-21")))
                        .jsonMapper(objectMapper)
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
                               .extension(AgentSessionExtension.builder()
                                                  .sessionStore(new InMemorySessionStore())
                                                  .updateSummaryAfterSession(true)
                                                  .mapper(objectMapper)
                                                  .build())
                               .build())
                .build()
                .registerToolbox(toolbox);


        final var modelSettings = ModelSettings.builder().temperature(0.1f).build();
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var response = agent.execute(new UserInput("Hi"),
                                           requestMetadata,
                                           null,
                                           null,
                                           List.of());
        log.info("Agent response: {}", response.getData());


        final var response2 = agent.execute(
                new UserInput("How is the weather at user's location?"),
                requestMetadata,
                model,
                modelSettings,
                response.getAllMessages());
        log.info("Second call: {}", response2.getData());
        log.debug("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response2.getAllMessages()));
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
    }
}