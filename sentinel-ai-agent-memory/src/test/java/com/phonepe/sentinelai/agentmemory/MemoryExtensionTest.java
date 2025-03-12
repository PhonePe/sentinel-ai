package com.phonepe.sentinelai.agentmemory;

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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 *
 */
@Slf4j
class MemoryExtensionTest {
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

        @Override
        public String name() {
            return "simple-agent";
        }
    }

    class InMemoryMemStore implements AgentMemoryStore {

        private record Key(MemoryScope scope, String scopeId) {
        }

        private Map<Key, List<AgentMemory>> memories = new ConcurrentHashMap<>();

        @Override
        public List<AgentMemory> findMemories(
                String scopeId,
                MemoryScope scope,
                Set<MemoryType> memoryTypes,
                String query,
                List<String> topics,
                int count) {
            return memories.getOrDefault(new Key(scope, scopeId), List.of());
        }

        @Override
        public Optional<AgentMemory> createOrUpdate(AgentMemory agentMemory) {
            log.info("recevied memory: {}", agentMemory);
            final var key = new Key(agentMemory.getScope(), agentMemory.getScopeId());
            final var memsInScope = memories.computeIfAbsent(key,
                                                             k -> new ArrayList<>());
            memsInScope.add(agentMemory);
            return Optional.of(agentMemory);
        }

        @Override
        public Optional<AgentMemory> updateMemory(
                MemoryScope scope,
                String scopeId,
                String name,
                UnaryOperator<AgentMemory> updater) {
            return Optional.empty();
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
        final var memoryOptions = AgentMemoryOptions.builder()
                .memoryStore(new InMemoryMemStore())
                .numMessagesForSummarization(3)
                .saveMemoryAfterSessionEnd(true)
                .updateSessionSummary(true)
                .build();
        final var modelSettings = ModelSettings.builder().temperature(0.1f).build();
        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        {

            final var agent = SimpleAgent.builder()
                    .setup(AgentSetup.builder()
                                   .mapper(objectMapper)
                                   .model(model)
                                   .modelSettings(ModelSettings.builder().temperature(0.1f).build())
                                   .extensions(List.of(MemoryExtension.builder()
                                                               .objectMapper(objectMapper)
                                                               .options(memoryOptions)
                                                               .build()))
                                   .build())
                    .build()
                    .registerToolbox(toolbox);
            final var response = agent.execute(new UserInput("Hi"),
                                               requestMetadata,
                                               null,
                                               null,
                                               List.of());
            log.info("Agent response: {}", response.getData().message());
        }


        {
            final var agent = SimpleAgent.builder()
                    .setup(AgentSetup.builder()
                                   .mapper(objectMapper)
                                   .model(model)
                                   .modelSettings(ModelSettings.builder().temperature(0.1f).build())
                                   .extensions(List.of(MemoryExtension.builder()
                                                               .objectMapper(objectMapper)
                                                               .options(memoryOptions)
                                                               .build()))
                                   .build())
                    .build()
                    .registerToolbox(toolbox);
            final var response2 = agent.execute(
                    new UserInput("How is the weather here?"),
                    requestMetadata,
                    model,
                    modelSettings,
                    List.of());
            log.info("Second call: {}", response2.getData());
            log.debug("Messages: {}", objectMapper.writerWithDefaultPrettyPrinter()
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
    }
}