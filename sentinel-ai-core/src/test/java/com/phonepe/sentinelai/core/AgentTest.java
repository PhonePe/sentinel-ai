package com.phonepe.sentinelai.core;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.phonepe.sentinelai.core.agent.Agent;
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

/**
 * Tests out basic functionality for the agent framework
 */
@Slf4j
class AgentTest {

    public record OutputObject(
            String username,

            String message) {
    }

    @JsonClassDescription("Parameter to be passed to get salutation for a user")
    public record SalutationParams(@JsonPropertyDescription("Name of the user") String name) {
    }

    @JsonClassDescription("User input")
    public record UserInput(String data) {
    }

    public record Salutation(List<String> salutation) {
    }

    public static class SimpleAgent extends Agent<UserInput, Void, OutputObject, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup, Map<String, CallableTool> tools) {
            super(OutputObject.class, "greet the user", setup, tools);
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
                               .modelSettings(ModelSettings.builder().temperature(0.1f).build())
                               .build())
                .build()
                .registerToolbox(toolbox);


        final var modelSettings = ModelSettings.builder().temperature(0.1f).build();
        final var response = agent.execute(new UserInput("Hi"), null, null, List.of());
        log.info("Agent response: {}", response.getData().message());


        final var response2 = agent.execute(new UserInput("How is the weather at user's location?"),
                                       model,
                                       modelSettings,
                                       response.getAllMessages());
        log.info("Second call: {}", response2.getData());
        log.info("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response2.getAllMessages()));
    }

    /**
     *
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