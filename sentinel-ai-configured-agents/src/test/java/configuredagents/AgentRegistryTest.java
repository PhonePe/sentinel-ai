package configuredagents;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.remotehttp.*;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;
import configuredagents.capabilities.AgentCapabilities;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;
import static com.phonepe.sentinelai.core.utils.TestUtils.ensureOutputGenerated;
import static org.junit.jupiter.api.Assertions.*;
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
        public PlannerAgent(
                @NonNull AgentSetup setup,
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
    void testSimpleAgent(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(4, "art.sa", getClass());
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var agentFactory = ConfiguredAgentFactory.builder() //Nothing is specified
                .build();
        final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();
        registerSummmarizingAgent(registry);
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(MAPPER)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                MAPPER
        );

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
                                                           .build())
                .join();
        printAgentResponse(response);
    }


    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateBadAgents")
    void testSimpleAgentFailure(String prefix, int numMocks, ConfiguredAgent badAgent, WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(numMocks, prefix, getClass());
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory((config, agent) -> badAgent)
                .build();
        final var summarizerAgentConfig = AgentConfiguration.builder()
                .agentName("Summarizer Agent")
                .description("Summarizes input text")
                .prompt("Provide a 140 character summary for the provided input text")
                .capability(AgentCapabilities.remoteHttpCalls(Map.of("weatherserver",
                                                                     Set.of("get_weather_for_location"))))
                .capability(AgentCapabilities.mcpCalls(Map.of("mcp", Set.of("add"))))
                .build();
        log.info("Summarizing agent id: {}",
                 registry.configureAgent(summarizerAgentConfig)
                         .map(AgentMetadata::getId)
                         .orElseThrow());

        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(MAPPER)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                MAPPER
        );

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
                                                           .build())
                .join();
        printAgentResponse(response);
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateFailurePrompt")
    void testSimpleAgentWrongAgentID(String filePrefix, int numMocks, String prompt, WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(numMocks, filePrefix, getClass());
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var agentFactory = ConfiguredAgentFactory.builder() //Nothing is specified
                .build();
        final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();
        registerSummmarizingAgent(registry);
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(MAPPER)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                MAPPER
        );

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
        final var response = topAgent.executeAsync(
                        AgentInput.<String>builder()
                                .request(prompt)
                                .build())
                .join();
        printAgentResponse(response);
        assertTrue(response.getAllMessages().stream()
                           .anyMatch(agentMessage -> {
                               if (agentMessage instanceof ToolCallResponse errorMessage) {
                                   return errorMessage.getErrorType().equals(ErrorType.TOOL_CALL_PERMANENT_FAILURE);
                               }
                               return false;
                           }));
    }

    @Test
    @SneakyThrows
    void testHttp(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "art.http", getClass());

        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "location" : "Bangalore",
                                                         "temperature" : "33 centigrade",
                                                         "condition" : "sunny"
                                                         }
                                                         """, 200)));
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
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
                                                              .description(
                                                                      "Provides the weather information for a given " +
                                                                              "location.")
                                                              .parameters(Map.of("location",
                                                                                 HttpToolMetadata.HttpToolParameterMeta.builder()
                                                                                         .description(
                                                                                                 "Location for the " +
                                                                                                         "user")
                                                                                         .type(HttpToolParameterType.STRING)
                                                                                         .build()))
                                                              .build())
                                            .template(HttpCallTemplate.builder()
                                                              .method(HttpCallSpec.HttpMethod.GET)
                                                              .path(HttpCallTemplate.Template.textSubstitutor(
                                                                      "/api/v1/weather/${location}")).build())
                                            .build()));
        final var agentFactory = ConfiguredAgentFactory.builder()
                .httpToolboxFactory(HttpToolboxFactory.builder()
                                            .toolConfigSource(toolSource)
                                            .okHttpClient(okHttpClient)
                                            .objectMapper(MAPPER)
                                            .upstreamResolver(upstream -> new UpstreamResolver() {
                                                @Override
                                                public String resolve(String upstream) {
                                                    return wiremock.getHttpBaseUrl();
                                                }
                                            })
                                            .build())
                .build();
        final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
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
                .capability(AgentCapabilities.remoteHttpCalls(Map.of("weatherserver",
                                                                     Set.of("get_weather_for_location"))))
                .build();
        log.info("Weather agent id: {}",
                 registry.configureAgent(weatherAgentConfiguration)
                         .map(AgentMetadata::getId)
                         .orElseThrow());

        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(MAPPER)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                MAPPER
        );

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
                                                           .build())
                .join();
        printAgentResponse(response);
        assertTrue(response.getData().matches(".*[sS]unny.*"));
        ensureOutputGenerated(response);
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateMcpTBFactory")
    void testMCP(
            String mockFilePrefix,
            int numMockFiles,
            final MCPToolBoxFactory toolBoxFactory,
            WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(numMockFiles, mockFilePrefix, getClass());

        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        final var params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-everything")
                .build();
        final var transport = new StdioClientTransport(params);

        try (final var mcpClient = McpClient.sync(transport)
                .build()) {
            mcpClient.initialize();
            final var agentFactory = ConfiguredAgentFactory.builder()
                    .mcpToolboxFactory(toolBoxFactory)
                    .build();
            final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
                    .agentSource(agentSource)
                    .agentFactory(agentFactory::createAgent)
                    .build();

            // Let's create weather agent configuration

            final var mathAgentConfig = AgentConfiguration.builder()
                    .agentName("Math Agent")
                    .description("Provides simple math operations.")
                    .prompt("Respond with the answer for provided query.")
                    .capability(AgentCapabilities.mcpCalls(Map.of("mcp", Set.of("add"))))
                    .build();
            log.info("Math agent id: {}",
                     registry.configureAgent(mathAgentConfig)
                             .map(AgentMetadata::getId)
                             .orElseThrow());

            final var model = new SimpleOpenAIModel<>(
                    "gpt-4o",
                    SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                            .baseUrl(wiremock.getHttpBaseUrl())
                            .apiKey("BLAH")
                            .apiVersion("2024-10-21")
                            .objectMapper(MAPPER)
                            .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                            .build(),
                    MAPPER
            );

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
                                                               .build())
                    .join();
            printAgentResponse(response);
            assertTrue(response.getData().matches(".*9.*"));
            ensureOutputGenerated(response);
        }
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("generateAgentConfig")
    void testCustomToolBox(AgentConfiguration weatherAgentConfiguration, WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "art.ctb", getClass());

        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "location" : "Bangalore",
                                                         "temperature" : "33 centigrade",
                                                         "condition" : "sunny"
                                                         }
                                                         """, 200)));
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
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
        final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();

        // Let's create weather agent configuration
        log.info("Weather agent id: {}",
                 registry.configureAgent(weatherAgentConfiguration)
                         .map(AgentMetadata::getId)
                         .orElseThrow());

        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(MAPPER)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                MAPPER
        );

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
                                                           .build())
                .join();
        printAgentResponse(response);
        assertTrue(response.getData().matches(".*[sS]unny.*"));
        ensureOutputGenerated(response);
    }

    @Test
    @SneakyThrows
    void testInheritance(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(4, "arti.http", getClass());

        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "location" : "Bangalore",
                                                         "temperature" : "33 centigrade",
                                                         "condition" : "sunny"
                                                         }
                                                         """, 200)));
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
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
                                                              .description(
                                                                      "Provides the weather information for a given " +
                                                                              "location.")
                                                              .parameters(Map.of("location",
                                                                                 HttpToolMetadata.HttpToolParameterMeta.builder()
                                                                                         .description(
                                                                                                 "Location for the " +
                                                                                                         "user")
                                                                                         .type(HttpToolParameterType.STRING)
                                                                                         .build()))
                                                              .build())
                                            .template(HttpCallTemplate.builder()
                                                              .method(HttpCallSpec.HttpMethod.GET)
                                                              .path(HttpCallTemplate.Template.textSubstitutor(
                                                                      "/api/v1/weather/${location}")).build())
                                            .build()));
        final var agentFactory = ConfiguredAgentFactory.builder()
                .httpToolboxFactory(HttpToolboxFactory.builder()
                                            .toolConfigSource(toolSource)
                                            .okHttpClient(okHttpClient)
                                            .objectMapper(MAPPER)
                                            .upstreamResolver(upstream -> new UpstreamResolver() {
                                                @Override
                                                public String resolve(String upstream) {
                                                    return wiremock.getHttpBaseUrl();
                                                }
                                            })
                                            .build())
                .build();
        final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();

        // Let's create weather agent configuration

        final var weatherAgentConfiguration = AgentConfiguration.builder()
                .agentName("Weather Agent")
                .description("Provides the weather information for a given location. Planner must call me instead of "
                        + "directly calling the weather tools.")
                .prompt("Respond with the current weather for the given location.")
                .inputSchema(schema(WeatherAgentInput.class))
                .outputSchema(schema(WeatherAgentOutput.class))
                .capability(AgentCapabilities.inheritToolsFromParent(Set.of("get_weather_for_location")))
                .build();
        registry.configureAgent(weatherAgentConfiguration)
                .map(AgentMetadata::getId)
                .orElseThrow();
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
                SimpleOpenAIAzure.builder()
//                        .baseUrl(EnvLoader.readEnv("AZURE_ENDPOINT"))
//                        .apiKey(EnvLoader.readEnv("AZURE_API_KEY"))
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .apiKey("BLAH")
                        .apiVersion("2024-10-21")
                        .objectMapper(MAPPER)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                MAPPER
        );

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
                                         .upstreamResolver(upstream -> wiremock.getHttpBaseUrl())
                                       .mapper(MAPPER)
                                       .build());
        final var response = topAgent.executeAsync(AgentInput.<String>builder()
                                                           .request("How is the weather in Bangalore?")
                                                           .build())
                .join();
        printAgentResponse(response);
        assertTrue(response.getData().matches(".*[sS]unny.*"));
        ensureOutputGenerated(response);
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
        final var registry = AgentRegistry.<String, String, PlannerAgent>builder()
                .agentSource(agentSource)
                .agentFactory(agentFactory::createAgent)
                .build();
        final var agents = registry.loadAgentsFromFile(Paths.get(Objects.requireNonNull(getClass().getResource(
                "/agent.json")).toURI()).toString());
        assertEquals(2, agents.size());

        assertThrows(IOException.class, () -> registry.loadAgentsFromFile("non-existent-file.json"));
        assertThrows(JsonProcessingException.class, () -> registry.loadAgentsFromFile(
                Paths.get(Objects.requireNonNull(
                        getClass().getResource("/agent-malformed.json")).toURI()).toString()));
    }

    private static Stream<Arguments> generateAgentConfig() {
        return Stream.of(
                Arguments.of(AgentConfiguration.builder()
                                     .agentName("Weather Agent")
                                     .description("Provides the weather information for a given location.")
                                     .prompt("Respond with the current weather for the given location.")
                                     .inputSchema(loadSchema("inputschema.json"))
                                     .capability(AgentCapabilities.customToolCalls(Set.of("getWeather")))
                                     .outputSchema(loadSchema("outputschema.json"))
                                     .build()),
                Arguments.of(AgentConfiguration.builder()
                                     .agentName("Weather Agent")
                                     .description("Provides the weather information for a given location.")
                                     .prompt("Respond with the current weather for the given location.")
                                     .inputSchema(loadSchema("inputschema.json"))
                                     .capability(AgentCapabilities.customToolCalls(Set.of()))
                                     .outputSchema(loadSchema("outputschema.json"))
                                     .build()
                            )
                        );
    }

    public static Stream<Arguments> generateMcpTBFactory() {
        final var params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-everything")
                .build();
        final var transport = new StdioClientTransport(params);
        final var mcpClient = McpClient.sync(transport)
                .build();
        return Stream.of(
                Arguments.of("art.mcp", 5,
                             MCPToolBoxFactory.builder()
                                     .objectMapper(MAPPER)
                                     .clientProvider(upstream -> Optional.of(mcpClient))
                                     .build()),
                Arguments.of("art.mcpf", 5,
                             MCPToolBoxFactory.builder()
                                     .objectMapper(MAPPER)
                                     .clientProvider(upstream -> Optional.empty())
                                     .build()
                                     .loadFromFile(Objects.requireNonNull(
                                             AgentRegistryTest.class.getResource("/mcp.json")).getPath())),
                Arguments.of("art.mcp", 5,
                             MCPToolBoxFactory.builder()
                                     .objectMapper(MAPPER)
                                     .clientProvider(upstream -> Optional.empty())
                                     .build()
                                     .registerMcpClient("mcp", mcpClient))

                        );
    }

    public static Stream<Arguments> generateFailurePrompt() {
        return Stream.of(
                Arguments.of("wmg",
                             2,
                             " Call tool agent_registry_get_agent_metadata with wrong agent id. Fail on error."),
                Arguments.of("wmi", 2, " Call agent_registry_invoke_agent with wrong agent id. Fail on error.")
                        );
    }

    public static Stream<Arguments> generateBadAgents() {
        final var failedAgent = mock(ConfiguredAgent.class);
        when(failedAgent.executeAsync(ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(AgentOutput.error(
                        List.of(),
                        new ModelUsageStats(),
                        SentinelError.error(ErrorType.GENERIC_MODEL_CALL_FAILURE, "Test error"))));

        final var errorAgent = mock(ConfiguredAgent.class);
        when(errorAgent.executeAsync(ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Error summarizing text")));

        return Stream.of(
                Arguments.of("bae", 3, failedAgent),
                Arguments.of("baex", 4, errorAgent));
    }

    private static void printAgentResponse(AgentOutput<String> response) throws JsonProcessingException {
        log.info("Agent response: {}", MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response.getData()));
    }

    @SneakyThrows
    private static JsonNode loadSchema(String schemaFilename) {
        return MAPPER.readTree(Files.readString(Path.of(Objects.requireNonNull(AgentRegistryTest.class.getResource(
                "/schema/%s".formatted(schemaFilename))).toURI())));
    }

    private static void registerSummmarizingAgent(AgentRegistry<String, String, PlannerAgent> registry) {
        final var summarizerAgentConfig = AgentConfiguration.builder()
                .agentName("Summarizer Agent")
                .description("Summarizes input text")
                .prompt("Provide a 140 character summary for the provided input text")
                .capability(AgentCapabilities.remoteHttpCalls(Map.of("weatherserver",
                                                                     Set.of("get_weather_for_location"))))
                .capability(AgentCapabilities.mcpCalls(Map.of("mcp", Set.of("add"))))
                .build();
        log.info("Summarizing agent id: {}",
                 registry.configureAgent(summarizerAgentConfig)
                         .map(AgentMetadata::getId)
                         .orElseThrow());
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

}