/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentMemoryExtension}
 */
@Slf4j
@WireMockTest
class AgentMemoryExtensionTest {
    public static class SimpleAgent extends Agent<UserInput, OutputObject, SimpleAgent> {
        @Builder
        public SimpleAgent(AgentSetup setup,
                           List<AgentExtension<UserInput, OutputObject, SimpleAgent>> extensions,
                           Map<String, ExecutableTool> tools) {
            super(OutputObject.class,
                  """
                          greet the user and respond to queries being posted.
                          IMPORTANT: you must extract memory about user for future use and to avoid tool calls.
                          """,
                  setup,
                  extensions,
                  tools);
        }

        @Tool("Get name of user")
        public String getName() {
            return "Santanu";
        }

        @Tool("Get salutation for user")
        public Salutation getSalutation(AgentRunContext<SalutationParams> context,
                                        @NonNull SalutationParams params) {
            return new Salutation(List.of("Mr", "Dr", "Prof"));
        }

        @Override
        public String name() {
            return "simple-agent";
        }
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

        @Tool("Get weather today")
        public String getWeatherToday(@JsonPropertyDescription("Name of user") final String location) {
            return location.equalsIgnoreCase("bangalore") ? "Sunny" : "unknown";
        }

        @Override
        public String name() {
            return AgentUtils.id(user);
        }
    }

    class InMemoryMemStore implements AgentMemoryStore {

        private final Map<Key, List<AgentMemory>> memories = new ConcurrentHashMap<>();

        @Override
        public List<AgentMemory> findMemories(String scopeId,
                                              MemoryScope scope,
                                              Set<MemoryType> memoryTypes,
                                              List<String> topics,
                                              String query,
                                              int minReusabilityScore,
                                              int count) {
            return memories.getOrDefault(new Key(scope, scopeId), List.of());
        }

        @Override
        public Optional<AgentMemory> save(AgentMemory agentMemory) {
            log.info("recevied memory: {}", agentMemory);
            final var key = new Key(agentMemory.getScope(),
                                    agentMemory.getScopeId());
            final var memsInScope = memories.computeIfAbsent(key,
                                                             k -> new ArrayList<>());
            memsInScope.add(agentMemory);
            return Optional.of(agentMemory);
        }

        private record Key(
                MemoryScope scope,
                String scopeId) {
        }

    }

    public record OutputObject(
            String username,
            String message) {
    }

    public record Salutation(
            List<String> salutation) {
    }

    @JsonClassDescription("Parameter to be passed to get salutation for a user")
    public record SalutationParams(
            @JsonPropertyDescription("Name of the user") String name) {
    }

    @JsonClassDescription("User input")
    public record UserInput(
            String data) {
    }

    @Test
    @SneakyThrows
    void testInlineExtraction(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(9, "met.inline", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var httpClient = new OkHttpClient.Builder().build();
        final var model = new SimpleOpenAIModel<>("global:LLM_GLOBAL_GPT_4O_PRD",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(objectMapper)
                                                          .clientAdapter(new OkHttpClientAdapter(httpClient))
                                                          .build(),
                                                  objectMapper);

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
                .extensions(List.of(AgentMemoryExtension
                        .<UserInput, OutputObject, SimpleAgent>builder()
                        .objectMapper(objectMapper)
                        .memoryStore(memoryStore)
                        .memoryExtractionMode(MemoryExtractionMode.INLINE)
                        .build()))
                .build()
                .registerToolbox(toolbox);
        {


            final var response = agent.execute(AgentInput.<UserInput>builder()
                    .request(new UserInput("Hi"))
                    .requestMetadata(requestMetadata)
                    .build());
            log.info("Agent response: {}", response.getData().message());
        }


        {
            final var response2 = agent.execute(AgentInput.<UserInput>builder()
                    .request(new UserInput("How is the weather here?"))
                    .requestMetadata(requestMetadata)
                    .build());
            log.info("Second call: {}", response2.getData());
            if (log.isTraceEnabled()) {
                log.trace("Messages: {}",
                          objectMapper.writerWithDefaultPrettyPrinter()
                                  .writeValueAsString(response2
                                          .getAllMessages()));
            }
            assertTrue(response2.getData().message().contains("sunny"));
        }
        assertFalse(memoryStore.memories.get(new InMemoryMemStore.Key(
                                                                      MemoryScope.ENTITY,
                                                                      "ss"))
                .isEmpty());
    }

    @Test
    @SneakyThrows
    void testOutOfBandExtraction(final WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(11, "met.async", getClass());
        final var objectMapper = JsonUtils.createMapper();
        final var toolbox = new TestToolBox("Santanu");
        final var httpClient = new OkHttpClient.Builder().build();
        final var model = new SimpleOpenAIModel<>("global:LLM_GLOBAL_GPT_4O_PRD",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(objectMapper)
                                                          .clientAdapter(new OkHttpClientAdapter(httpClient))
                                                          .build(),
                                                  objectMapper);

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
                        .executorService(Executors.newFixedThreadPool(5))
                        .build())
                .extensions(List.of(AgentMemoryExtension
                        .<UserInput, OutputObject, SimpleAgent>builder()
                        .objectMapper(objectMapper)
                        .memoryStore(memoryStore)
                        .memoryExtractionMode(MemoryExtractionMode.OUT_OF_BAND)
                        .build()))
                .build()
                .registerToolbox(toolbox);
        {


            final var response = agent.execute(AgentInput.<UserInput>builder()
                    .request(new UserInput("Hi"))
                    .requestMetadata(requestMetadata)
                    .build());
            log.info("Agent response: {}", response.getData().message());
        }

        Awaitility.await()
                .pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofMinutes(1))
                .until(() -> !memoryStore.memories.isEmpty());
//                .until(() -> memoryStore.memories.get(new InMemoryMemStore.Key(MemoryScope.ENTITY, "ss")) != null);
        final var currMemories = memoryStore.memories.size();

        {
            final var response2 = agent.execute(AgentInput.<UserInput>builder()
                    .request(new UserInput("How is the weather here?"))
                    .requestMetadata(requestMetadata)
                    .build());
            log.info("Second call: {}", response2.getData());
            if (log.isTraceEnabled()) {
                log.trace("Messages: {}",
                          objectMapper.writerWithDefaultPrettyPrinter()
                                  .writeValueAsString(response2
                                          .getAllMessages()));
            }
            assertNotNull(response2.getData());
            assertTrue(response2.getData().message().contains("sunny"));
        }
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> memoryStore.memories.size() > currMemories);
        assertFalse(memoryStore.memories.isEmpty());
    }
}
