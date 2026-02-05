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

package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilities;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.DefaultChatCompletionServiceFactory;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;
import com.phonepe.sentinelai.toolbox.mcp.MCPToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType;
import com.phonepe.sentinelai.toolbox.remotehttp.UpstreamResolver;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;
import static com.phonepe.sentinelai.core.utils.TestUtils.ensureOutputGenerated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
@WireMockTest
class AgentRegistryTest {

    private static final ObjectMapper MAPPER = JsonUtils.createMapper();

    private static final class PlannerAgent extends Agent<String, String, PlannerAgent> {
        @Builder
        public PlannerAgent(@NonNull AgentSetup setup,
                            @Singular List<AgentExtension<String, String, PlannerAgent>> extensions) {
            super(String.class,
                  """
                          Your role is to perform complex tasks as specified by the user. You can achieve this by using
                           the other agents. Do not attempt to answer the user's query directly. Instead, analyze the query and
                           determine which agent(s) would be best suited to handle different parts of the request. Plan
                           out a sequence of agent calls to fulfill the user's request comprehensively.
                           Once you have a plan, execute the agent calls in the determined order, passing relevant
                           information between them as needed. Finally, compile the results from all agent calls into a coherent response
                           to present to the user.
                           Remember, your primary function is to orchestrate the use of other agents to accomplish the user's
                           goals effectively. Do not try to perform the functionality of the other agents; instead, focus on leveraging their capabilities
                           through well-thought-out planning and execution.
                          """,
                  setup,
                  extensions,
                  Map.of());
        }

        @Override
        public String name() {
            return "planner-agent";
        }
    }


    @Value
    private static class WeatherAgentInput {
        @JsonPropertyDescription("Location to know weather for")
        String location;
    }

    @Value
    private static class WeatherAgentOutput {
        String condition;
        String temperature;
    }

    public static Stream<Arguments> generateBadAgents() {
        final var failedAgent = mock(ConfiguredAgent.class);
        when(failedAgent.executeAsync(ArgumentMatchers.any())).thenReturn(
                                                                          CompletableFuture
                                                                                  .completedFuture(AgentOutput
                                                                                          .error(List
                                                                                                  .of(),
                                                                                                 new ModelUsageStats(),
                                                                                                 SentinelError
                                                                                                         .error(ErrorType.GENERIC_MODEL_CALL_FAILURE,
                                                                                                                "Test error"))));

        final var errorAgent = mock(ConfiguredAgent.class);
        when(errorAgent.executeAsync(ArgumentMatchers.any())).thenReturn(
                                                                         CompletableFuture
                                                                                 .failedFuture(new RuntimeException("Error summarizing text")));

        return Stream.of(Arguments.of("bae", 3, failedAgent),
                         Arguments.of("baex", 4, errorAgent));
    }

    public static Stream<Arguments> generateFailurePrompt() {
        return Stream.of(Arguments.of("wmg",
                                      2,
                                      " Call function agent_registry_get_agent_metadata with wrong agent id. Fail on error."),
                         Arguments.of("wmi",
                                      2,
                                      " Call agent_registry_invoke_agent with wrong agent id. Fail on error."));
    }

    public static Stream<Arguments> generateMcpTBFactory() {
        final var params = ServerParameters.builder("npx")
                .args("-y",
                      "@modelcontextprotocol/server-everything@2025.12.18")
                .build();
        final var transport = new StdioClientTransport(params,
                                                       new JacksonMcpJsonMapper(MAPPER));
        final var mcpClient = McpClient.sync(transport).build();
        mcpClient.initialize();
        return Stream.of(Arguments.of("art.mcp",
                                      5,
                                      MCPToolBoxFactory.builder()
                                              .objectMapper(MAPPER)
                                              .clientProvider(upstream -> Optional
                                                      .of(mcpClient))
                                              .build()),
                         Arguments.of("art.mcpf",
                                      5,
                                      MCPToolBoxFactory.builder()
                                              .objectMapper(MAPPER)
                                              .clientProvider(upstream -> Optional
                                                      .empty())
                                              .build()
                                              .loadFromFile(Objects
                                                      .requireNonNull(AgentRegistryTest.class
                                                              .getResource("/mcp.json"))
                                                      .getPath())),
                         Arguments.of("art.mcp",
                                      5,
                                      MCPToolBoxFactory.builder()
                                              .objectMapper(MAPPER)
                                              .clientProvider(upstream -> Optional
                                                      .empty())
                                              .build()
                                              .registerMcpClient("mcp",
                                                                 mcpClient))

        );
    }

    public static Stream<Arguments> generateSimpleTestConfig() {
        return Stream.of(
                         //Direct invocation so one less call. Num mocks: 3
                         Arguments.of(null, 3, "artd.sa"), //Default is inject
                         Arguments.of(AgentMetadataAccessMode.INCLUDE_IN_PROMPT,
                                      3,
                                      "artp.sa"),
                         // Metadata get function will be called so one more call. Num mocks: 4
                         Arguments.of(
                                      AgentMetadataAccessMode.METADATA_TOOL_LOOKUP,
                                      4,
                                      "art.sa"));
    }

    private static Stream<Arguments> generateAgentConfig() {
        return Stream.of(Arguments.of(AgentConfiguration.builder()
                .agentName("Weather Agent")
                .description("Provides the weather information for a given location.")
                .prompt("Respond with the current weather for the given location.")
                .inputSchema(loadSchema("inputschema.json"))
                .capability(AgentCapabilities.customToolCalls(Set.of(
                                                                     "getWeather")))
                .outputSchema(loadSchema("outputschema.json"))
                .build()),
                         Arguments.of(AgentConfiguration.builder()
                                 .agentName("Weather Agent")
                                 .description("Provides the weather information for a given location.")
                                 .prompt("Respond with the current weather for the given location.")
                                 .inputSchema(loadSchema("inputschema.json"))
                                 .capability(AgentCapabilities.customToolCalls(
                                                                               Set.of()))
                                 .outputSchema(loadSchema("outputschema.json"))
                                 .build()));
    }

    @SneakyThrows
    private static JsonNode loadSchema(String schemaFilename) {
        return MAPPER.readTree(Files.readString(Path.of(Objects.requireNonNull(
                                                                               AgentRegistryTest.class
                                                                                       .getResource("/schema/%s"
                                                                                               .formatted(schemaFilename)))
                .toURI())));
    }

    private static DefaultChatCompletionServiceFactory multiModelProviderFactory(OkHttpClient okHttpClient,
                                                                                 WireMockRuntimeInfo wiremock) {
        return new DefaultChatCompletionServiceFactory()
                .registerDefaultProvider(SimpleOpenAIAzure.builder()
                        .baseUrl(TestUtils.getTestProperty("AZURE_ENDPOINT",
                                                           wiremock.getHttpBaseUrl()))
                        .apiKey(TestUtils.getTestProperty("AZURE_API_KEY",
                                                          "BLAH"))
                        .apiVersion("2024-10-21")
                        .objectMapper(MAPPER)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build())
                .registerProvider("gpt-5",
                                  SimpleOpenAIAzure.builder()
                                          .baseUrl(TestUtils.getTestProperty(
                                                                             "AZURE_GPT5_ENDPOINT",
                                                                             wiremock.getHttpBaseUrl()))
                                          .apiKey(TestUtils.getTestProperty(
                                                                            "AZURE_API_KEY",
                                                                            "BLAH"))
                                          .apiVersion("2024-10-21")
                                          .objectMapper(MAPPER)
                                          .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                                          .build());
    }


    private static void printAgentResponse(AgentOutput<String> response) throws JsonProcessingException {
        log.info("Agent response: {}",
                 MAPPER.writerWithDefaultPrettyPrinter()
                         .writeValueAsString(response.getData()));
    }

    private static void registerSummmarizingAgent(AgentRegistry<String, String, PlannerAgent> registry) {
        final var summarizerAgentConfig = AgentConfiguration.builder()
                .agentName("Summarizer Agent")
                .description("Summarizes input text")
                .prompt("Provide a 140 character summary for the provided input text")
                .capability(AgentCapabilities.remoteHttpCalls(Map.of(
                                                                     "weatherserver",
                                                                     Set.of("get_weather_for_location"))))
                .capability(AgentCapabilities.mcpCalls(Map.of("mcp",
                                                              Set.of("add"))))
                .modelConfiguration(ModelConfiguration.builder()
                        .name("gpt-5")
                        .settings(ModelSettings.builder()
                                .seed(42)
                                .temperature(1.0f)
                                .build())
                        .build())
                .build();
        log.info("Summarizing agent id: {}",
                 registry.configureAgent(summarizerAgentConfig)
                         .map(AgentMetadata::getId)
                         .orElseThrow());
    }

    @Tool("Provides the weather for a location")
    public String getWeather(@JsonPropertyDescription("Name of the city to get weather for") final String city) {
        return """
                {
                "location" : "Bangalore",
                "temperature" : "33 centigrade",
                "condition" : "sunny"
                }
                """;
    }

    @Test
    @SneakyThrows
    void testConfigLoading() {
        final var agentFactory = ConfiguredAgentFactory.builder()
                .customToolBox(CustomToolBox.builder()
                        .name("custom")
                        .build()
                        .registerToolsFromObject(this))
                .build();
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();
        final var agents = registry.loadAgentsFromFile(Paths.get(Objects
                .requireNonNull(getClass().getResource("/agent.json"))
                .toURI()).toString());
        assertEquals(2, agents.size());

        assertThrows(IOException.class,
                     () -> registry.loadAgentsFromFile(
                                                       "non-existent-file.json"));
        assertThrows(JsonProcessingException.class,
                     () -> registry.loadAgentsFromFile(Paths.get(Objects
                             .requireNonNull(getClass().getResource(
                                                                    "/agent-malformed.json"))
                             .toURI()).toString()));
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateAgentConfig")
    void testCustomToolBox(AgentConfiguration weatherAgentConfiguration,
                           WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "art.ctb", getClass());

        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore")).willReturn(
                                                                        jsonResponse("""
                                                                                {
                                                                                "location" : "Bangalore",
                                                                                "temperature" : "33 centigrade",
                                                                                "condition" : "sunny"
                                                                                }
                                                                                """,
                                                                                     200)));
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();

        final var agentFactory = ConfiguredAgentFactory.builder()
                .customToolBox(CustomToolBox.builder()
                        .name("custom")
                        .build()
                        .registerToolsFromObject(this))
                .build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();

        // Let's create weather agent configuration
        log.info("Weather agent id: {}",
                 registry.configureAgent(weatherAgentConfiguration)
                         .map(AgentMetadata::getId)
                         .orElseThrow());

        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(MAPPER)
                                                          .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                                                          .build(),
                                                  MAPPER);

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();

        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build();
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                .request("How is the weather in Bangalore?")
                .build()).join();
        printAgentResponse(response);
        assertTrue(response.getData().matches(".*[sS]unny.*"));
        ensureOutputGenerated(response);
        log.info("Model usage stats: {}", response.getUsage());
    }

    @Test
    @SneakyThrows
    void testHttp(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "art.http", getClass());

        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore")).willReturn(
                                                                        jsonResponse("""
                                                                                {
                                                                                "location" : "Bangalore",
                                                                                "temperature" : "33 centigrade",
                                                                                "condition" : "sunny"
                                                                                }
                                                                                """,
                                                                                     200)));
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();

        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(MAPPER)
                .build();
        toolSource.register("weatherserver",
                            List.of(TemplatizedHttpTool.builder()
                                    .metadata(HttpToolMetadata.builder()
                                            .name("get_weather_for_location")
                                            .description("Provides the weather information for a given " + "location.")
                                            .parameters(Map.of("location",
                                                               HttpToolMetadata.HttpToolParameterMeta
                                                                       .builder()
                                                                       .description("Location for the " + "user")
                                                                       .type(HttpToolParameterType.STRING)
                                                                       .build()))
                                            .build())
                                    .template(HttpCallTemplate.builder()
                                            .method(HttpCallSpec.HttpMethod.GET)
                                            .path(HttpCallTemplate.Template
                                                    .textSubstitutor("/api/v1/weather/${location}"))
                                            .build())
                                    .build()));
        final var agentFactory = ConfiguredAgentFactory.builder()
                .httpToolboxFactory(HttpToolboxFactory.builder()
                        .toolConfigSource(toolSource)
                        .okHttpClient(okHttpClient)
                        .objectMapper(MAPPER)
                        .upstreamResolver(upstream -> new UpstreamResolver() {
                            @Override
                            public String resolve(String upstream) {
                                return TestUtils.getTestProperty(
                                                                 "REMOTE_HTTP_ENDPOINT",
                                                                 wiremock.getHttpBaseUrl());
                            }
                        })
                        .build())
                .build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();

        // Let's create weather agent configuration

        final var weatherAgentConfiguration = AgentConfiguration.builder()
                .agentName("Weather Agent")
                .description("Provides the weather information for a given location.")
                .prompt("Respond with the current weather for the given location.")
                .inputSchema(schema(WeatherAgentInput.class))
                .outputSchema(schema(WeatherAgentOutput.class))
                .capability(AgentCapabilities.remoteHttpCalls(Map.of(
                                                                     "weatherserver",
                                                                     Set.of("get_weather_for_location"))))
                .build();
        log.info("Weather agent id: {}",
                 registry.configureAgent(weatherAgentConfiguration)
                         .map(AgentMetadata::getId)
                         .orElseThrow());

        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(MAPPER)
                                                          .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                                                          .build(),
                                                  MAPPER);

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();

        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build();
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                .request("How is the weather in Bangalore?")
                .build()).join();
        printAgentResponse(response);
        assertTrue(response.getData().matches(".*[sS]unny.*"));
        ensureOutputGenerated(response);
    }


    @Test
    @SneakyThrows
    void testInheritance(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(4, "arti.http", getClass());

        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore")).willReturn(
                                                                        jsonResponse("""
                                                                                {
                                                                                "location" : "Bangalore",
                                                                                "temperature" : "33 centigrade",
                                                                                "condition" : "sunny"
                                                                                }
                                                                                """,
                                                                                     200)));
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();

        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(MAPPER)
                .build();
        toolSource.register("weatherserver",
                            List.of(TemplatizedHttpTool.builder()
                                    .metadata(HttpToolMetadata.builder()
                                            .name("get_weather_for_location")
                                            .description("Provides the weather information for a given " + "location.")
                                            .parameters(Map.of("location",
                                                               HttpToolMetadata.HttpToolParameterMeta
                                                                       .builder()
                                                                       .description("Location for the " + "user")
                                                                       .type(HttpToolParameterType.STRING)
                                                                       .build()))
                                            .build())
                                    .template(HttpCallTemplate.builder()
                                            .method(HttpCallSpec.HttpMethod.GET)
                                            .path(HttpCallTemplate.Template
                                                    .textSubstitutor("/api/v1/weather/${location}"))
                                            .build())
                                    .build()));
        final var agentFactory = ConfiguredAgentFactory.builder()
                .httpToolboxFactory(HttpToolboxFactory.builder()
                        .toolConfigSource(toolSource)
                        .okHttpClient(okHttpClient)
                        .objectMapper(MAPPER)
                        .upstreamResolver(upstream -> new UpstreamResolver() {
                            @Override
                            public String resolve(String upstream) {
                                return TestUtils.getTestProperty(
                                                                 "REMOTE_HTTP_ENDPOINT",
                                                                 wiremock.getHttpBaseUrl());
                            }
                        })
                        .build())
                .build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();

        // Let's create weather agent configuration

        final var weatherAgentConfiguration = AgentConfiguration.builder()
                .agentName("Weather Agent")
                .description("Provides the weather information for a given location. Planner must call me instead of " + "directly calling the weather tools.")
                .prompt("Respond with the current weather for the given location.")
                .inputSchema(schema(WeatherAgentInput.class))
                .outputSchema(schema(WeatherAgentOutput.class))
                .capability(AgentCapabilities.inheritToolsFromParent(Set.of(
                                                                            "get_weather_for_location")))
                .build();
        registry.configureAgent(weatherAgentConfiguration)
                .map(AgentMetadata::getId)
                .orElseThrow();
        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(MAPPER)
                                                          .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                                                          .build(),
                                                  MAPPER);

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();

        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build()
                .registerToolbox(HttpToolBox.builder()
                        .upstream("weatherserver")
                        .httpToolSource(toolSource)
                        .httpClient(okHttpClient)
                        .upstreamResolver(upstream -> TestUtils.getTestProperty(
                                                                                "REMOTE_HTTP_ENDPOINT",
                                                                                wiremock.getHttpBaseUrl()))
                        .mapper(MAPPER)
                        .build());
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                .request("How is the weather in Bangalore?")
                .build()).join();
        printAgentResponse(response);
        assertTrue(response.getData().matches(".*[sS]unny.*"));
        ensureOutputGenerated(response);
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateMcpTBFactory")
    void testMCP(String mockFilePrefix,
                 int numMockFiles,
                 final MCPToolBoxFactory toolBoxFactory,
                 WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(numMockFiles, mockFilePrefix, getClass());

        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();

        final var agentFactory = ConfiguredAgentFactory.builder()
                .mcpToolboxFactory(toolBoxFactory)
                .build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .agentMetadataAccessMode(AgentMetadataAccessMode.METADATA_TOOL_LOOKUP)
                .build();

        // Let's create weather agent configuration

        final var mathAgentConfig = AgentConfiguration.builder()
                .agentName("Math Agent")
                .description("Provides simple math operations.")
                .prompt("Respond with the answer for provided query.")
                .capability(AgentCapabilities.mcpCalls(Map.of("mcp",
                                                              Set.of("add"))))
                .build();
        log.info("Math agent id: {}",
                 registry.configureAgent(mathAgentConfig)
                         .map(AgentMetadata::getId)
                         .orElseThrow());

        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(MAPPER)
                                                          .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                                                          .build(),
                                                  MAPPER);

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();


        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build();
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                .request("What is the sum of 3 and 6?")
                .build()).join();
        printAgentResponse(response);
        assertTrue(response.getData().matches(".*9.*"));
        ensureOutputGenerated(response);
    }

    @Test
    @SneakyThrows
    void testRealAgentRegistration(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(4, "art.reg", getClass());

        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var agentFactory = ConfiguredAgentFactory.builder().build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();
        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(MAPPER)
                                                          .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                                                          .build(),
                                                  MAPPER);

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();

        final var mathAgentConfig = AgentConfiguration.builder()
                .agentName("Math Agent")
                .description("Provides simple math operations.")
                .prompt("Respond with the answer for provided query.")
                .capability(AgentCapabilities.mcpCalls(Map.of("mcp",
                                                              Set.of("add"))))
                .build();
        final var params = ServerParameters.builder("npx")
                .args("-y",
                      "@modelcontextprotocol/server-everything@2025.12.18")
                .build();
        final var transport = new StdioClientTransport(params,
                                                       new JacksonMcpJsonMapper(MAPPER));
        try (final var mcpClient = McpClient.sync(transport).build()) {
            mcpClient.initialize();
            final var mathAgent = new MathAgent(mathAgentConfig, setup)
                    .registerToolbox(new MCPToolBox("mcp",
                                                    mcpClient,
                                                    MAPPER,
                                                    Set.of("add")));

            final var metadata = registry.configureAgent(mathAgent)
                    .orElseThrow();
            assertEquals("math_agent", metadata.getId());
            final var topAgent = PlannerAgent.builder()
                    .setup(setup)
                    .extension(registry)
                    .build();
            final var response = topAgent.executeAsync(AgentInput
                    .<String>builder()
                    .request("What is the sum of 3 and 6?")
                    .build()).join();
            printAgentResponse(response);
            assertTrue(response.getData().matches(".*9.*"));
            ensureOutputGenerated(response);
        }
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateSimpleTestConfig")
    void testSimpleAgent(AgentMetadataAccessMode metadataAccessMode,
                         int numMocks,
                         String mockPrefix,
                         WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(numMocks, mockPrefix, getClass());
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var agentFactory = ConfiguredAgentFactory.builder() //Nothing is specified
                .build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .agentMetadataAccessMode(metadataAccessMode)
                .build();
        registerSummmarizingAgent(registry);
        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  multiModelProviderFactory(okHttpClient,
                                                                            wiremock),
                                                  MAPPER,
                                                  SimpleOpenAIModelOptions
                                                          .builder()
                                                          .toolChoice(SimpleOpenAIModelOptions.ToolChoice.REQUIRED)
                                                          .build());

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();

        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build();
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                .request("Summarize the story of War and Peace")
                .build()).join();
        printAgentResponse(response);
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateBadAgents")
    void testSimpleAgentFailure(String prefix,
                                int numMocks,
                                ConfiguredAgent badAgent,
                                WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(numMocks, prefix, getClass());
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory((config, agent) -> badAgent)
                .build();
        final var summarizerAgentConfig = AgentConfiguration.builder()
                .agentName("Summarizer Agent")
                .description("Summarizes input text")
                .prompt("Provide a 140 character summary for the provided input text")
                .capability(AgentCapabilities.remoteHttpCalls(Map.of(
                                                                     "weatherserver",
                                                                     Set.of("get_weather_for_location"))))
                .capability(AgentCapabilities.mcpCalls(Map.of("mcp",
                                                              Set.of("add"))))
                .build();
        log.info("Summarizing agent id: {}",
                 registry.configureAgent(summarizerAgentConfig)
                         .map(AgentMetadata::getId)
                         .orElseThrow());

        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  SimpleOpenAIAzure.builder()
                                                          .baseUrl(TestUtils
                                                                  .getTestProperty("AZURE_ENDPOINT",
                                                                                   wiremock.getHttpBaseUrl()))
                                                          .apiKey(TestUtils
                                                                  .getTestProperty("AZURE_API_KEY",
                                                                                   "BLAH"))
                                                          .apiVersion("2024-10-21")
                                                          .objectMapper(MAPPER)
                                                          .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                                                          .build(),
                                                  MAPPER);

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();

        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build();
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                .request("Summarize the story of War and Peace")
                .build()).join();
        printAgentResponse(response);
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateFailurePrompt")
    void testSimpleAgentWrongAgentID(String filePrefix,
                                     int numMocks,
                                     String prompt,
                                     WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(numMocks, filePrefix, getClass());
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder().callTimeout(Duration
                .ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var agentFactory = ConfiguredAgentFactory.builder() //Nothing is specified
                .build();
        final var registry = AgentRegistry
                .<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .agentMetadataAccessMode(AgentMetadataAccessMode.METADATA_TOOL_LOOKUP)
                .build();
        registerSummmarizingAgent(registry);
        final var model = new SimpleOpenAIModel<>("gpt-4o",
                                                  multiModelProviderFactory(okHttpClient,
                                                                            wiremock),
                                                  MAPPER,
                                                  SimpleOpenAIModelOptions
                                                          .builder()
                                                          .toolChoice(SimpleOpenAIModelOptions.ToolChoice.REQUIRED)
                                                          .build());

        final var setup = AgentSetup.builder()
                .mapper(MAPPER)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(0)
                        .parallelToolCalls(false)
                        .build())
                .build();

        final var topAgent = PlannerAgent.builder()
                .setup(setup)
                .extension(registry)
                .build();
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                .request(prompt)
                .build()).join();
        printAgentResponse(response);
        assertTrue(response.getAllMessages().stream().anyMatch(agentMessage -> {
            if (agentMessage instanceof ToolCallResponse errorMessage) {
                return errorMessage.getErrorType()
                        .equals(ErrorType.TOOL_CALL_PERMANENT_FAILURE);
            }
            return false;
        }));
    }

}
