package configuredagents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolParameterType;
import com.phonepe.sentinelai.toolbox.remotehttp.UpstreamResolver;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.InMemoryHttpToolSource;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.TemplatizedHttpTool;
import configuredagents.capabilities.AgentCapabilities;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@Slf4j
@WireMockTest
class AgentRegistryTest {

    private static final class PlannerAgent extends Agent<String, String, PlannerAgent> {
        @Builder
        public PlannerAgent(@NonNull AgentSetup setup, @Singular List<AgentExtension<String, String, PlannerAgent>> extensions) {
            super(String.class,
                  """
                          Your role is to perform complex tasks as specified by the user. You can achieve this by using
                           the other agents.
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

    @Test
    @SneakyThrows
    void testHttp(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "me", getClass());

        stubFor(get(urlEqualTo("/api/v1/weather/Bangalore"))
                        .willReturn(jsonResponse("""
                                                         {
                                                         "location" : "Bangalore",
                                                         "temperature" : "33 centigrade",
                                                         "condition" : "sunny"
                                                         }
                                                         """, 200)));
        final var agentSource = new InMemoryAgentConfigurationSource();
        final var objectMapper = JsonUtils.createMapper();
        final var okHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(180))
                .connectTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .build();

        final var toolSource = InMemoryHttpToolSource.builder()
                .mapper(objectMapper)
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
                .httpToolboxFactory(new HttpToolboxFactory(okHttpClient,
                                                             objectMapper,
                                                             toolSource,
                                                             upstream -> new UpstreamResolver() {
                                                                 @Override
                                                                 public String resolve(String upstream) {
                                                                     return wiremock.getHttpBaseUrl();
                                                                 }
                                                             }))
                .mcpToolboxFactory(MCPToolBoxFactory.builder()
                                           .objectMapper(objectMapper)
                                           .clientProvider(upstream -> {
                                               throw new IllegalStateException("MCP is not supported in this test");
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
                .inputSchema(loadSchema(objectMapper, "inputschema.json"))
                .capability(AgentCapabilities.remoteHttpCalls(Map.of("weatherserver", Set.of("get_weather_for_location"))))
                .outputSchema(loadSchema(objectMapper, "outputschema.json"))
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
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                objectMapper
        );

        final var setup = AgentSetup.builder()
                .mapper(objectMapper)
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
        log.info("Agent response: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response.getData()));
        assertTrue(response.getData().matches(".*[sS]unny.*"));
    }

    @Test
    @SneakyThrows
    void testMCP(WireMockRuntimeInfo wiremock) {
        TestUtils.setupMocks(5, "mc", getClass());

        final var agentSource = new InMemoryAgentConfigurationSource();
        final var objectMapper = JsonUtils.createMapper();
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

        final var mcpClient = McpClient.sync(transport)
                .build();
        mcpClient.initialize();
        final var agentFactory = ConfiguredAgentFactory.builder()
                .httpToolboxFactory(new HttpToolboxFactory(okHttpClient,
                                                             objectMapper,
                                                             new InMemoryHttpToolSource(),
                                                             upstream -> new UpstreamResolver() {
                                                                 @Override
                                                                 public String resolve(String upstream) {
                                                                     return wiremock.getHttpBaseUrl();
                                                                 }
                                                             }))
                .mcpToolboxFactory(MCPToolBoxFactory.builder()
                                           .objectMapper(objectMapper)
                                           .clientProvider(upstream -> {
                                                  if (upstream.equals("mcp")) {
                                                    return Optional.of(mcpClient);
                                                  }
                                                  return Optional.empty();
                                           })
                                           .build())
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
                        .objectMapper(objectMapper)
                        .clientAdapter(new OkHttpClientAdapter(okHttpClient))
                        .build(),
                objectMapper
        );

        final var setup = AgentSetup.builder()
                .mapper(objectMapper)
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
        log.info("Agent response: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(response.getData()));
        assertTrue(response.getData().matches(".*9.*"));
    }

    @SneakyThrows
    private JsonNode loadSchema(ObjectMapper mapper, String schemaFilename) {
        return mapper.readTree(Files.readString(Path.of(Objects.requireNonNull(getClass().getResource(
                "/schema/%s".formatted(schemaFilename))).toURI())));
    }
}