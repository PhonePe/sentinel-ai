package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentMemoryExtension}
 */
@Slf4j
@WireMockTest
class AgentMemoryExtensionTest {
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
        public SimpleAgent(AgentSetup setup, List<AgentExtension> extensions, Map<String, ExecutableTool> tools) {
            super(OutputObject.class, "greet the user", setup, extensions, tools);
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

    class InMemoryMemStore implements AgentMemoryStore {

        private record Key(MemoryScope scope, String scopeId) {
        }

        private Map<Key, List<AgentMemory>> memories = new ConcurrentHashMap<>();

        @Override
        public List<AgentMemory> findMemories(
                String scopeId,
                MemoryScope scope,
                Set<MemoryType> memoryTypes,
                List<String> topics, String query,
                int minReusabilityScore, int count) {
            return memories.getOrDefault(new Key(scope, scopeId), List.of());
        }

        @Override
        public Optional<AgentMemory> save(AgentMemory agentMemory) {
            log.info("recevied memory: {}", agentMemory);
            final var key = new Key(agentMemory.getScope(), agentMemory.getScopeId());
            final var memsInScope = memories.computeIfAbsent(key,
                                                             k -> new ArrayList<>());
            memsInScope.add(agentMemory);
            return Optional.of(agentMemory);
        }

    }

    @Test
    @SneakyThrows
    void test(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(6, "me", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var httpClient = new OkHttpClient.Builder()
                .build();
        final var model = new SimpleOpenAIModel<>(
                "global:LLM_GLOBAL_GPT_4O_PRD",
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

        final var requestMetadata = AgentRequestMetadata.builder()
                .sessionId("s1")
                .userId("ss")
                .build();
        final var memoryStore = new InMemoryMemStore();
        final var agent = SimpleAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(objectMapper)
                               .model(model)
                               .modelSettings(ModelSettings.builder()
                                                      .temperature(0f)
                                                      .seed(42)
                                                      .parallelToolCalls(false)
                                                      .build())
                               .build())
                .extensions(List.of(AgentMemoryExtension.builder()
                                            .objectMapper(objectMapper)
                                            .memoryStore(memoryStore)
                                            .saveMemoryAfterSessionEnd(true)
                                            .build()))
                .build()
                .registerToolbox(toolbox);
        {


            final var response = agent.execute(
                    AgentInput.<UserInput>builder()
                            .request(new UserInput("Hi"))
                            .requestMetadata(requestMetadata)
                            .build());
            log.info("Agent response: {}", response.getData().message());
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