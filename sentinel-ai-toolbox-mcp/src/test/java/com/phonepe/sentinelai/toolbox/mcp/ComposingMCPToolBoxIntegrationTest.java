package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPJsonReader;
import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAIAzure;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import lombok.NonNull;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.phonepe.sentinelai.core.utils.TestUtils.assertNoFailedToolCalls;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@WireMockTest
class ComposingMCPToolBoxIntegrationTest {
    private static class CompositeMCPTestAgent extends Agent<String, String, CompositeMCPTestAgent> {

        public CompositeMCPTestAgent(
                @NonNull AgentSetup setup,
                Map<String, ExecutableTool> knownTools) {
            super(String.class,
                  """
                          Respond to user's queries. Use the provided tools to get the correct information.
                          """, setup, List.of(), knownTools);
        }

        @Override
        public String name() {
            return "composite-mcp-test-agent";
        }
    }

    @Test
    @SneakyThrows
    void test(final WireMockRuntimeInfo wiremock) {
        testToolBox(wiremock, this::buildToolBox);
    }

    @Test
    @SneakyThrows
    void testBuildFromFile(final WireMockRuntimeInfo wiremock) {
        testToolBox(wiremock, this::buildToolBoxFromFile);
    }

    @Test
    @SneakyThrows
    void testBuildFromFileWithTools(final WireMockRuntimeInfo wiremock) {
        testToolBox(wiremock, this::buildToolBoxFromFileWithTools);
    }

    void testToolBox(final WireMockRuntimeInfo wiremock,
                     Function<JsonMapper, ComposingMCPToolBox> toolboxBuilder) {
        TestUtils.setupMocks(2, "tc", getClass());
        final var httpClient = new OkHttpClient.Builder()
                .build();
        final var objectMapper = JsonUtils.createMapper();
        final var model = new SimpleOpenAIModel<>(
                "gpt-4o",
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

        final var agent = new CompositeMCPTestAgent(
                AgentSetup.builder()
                        .mapper(objectMapper)
                        .model(model)
                        .modelSettings(ModelSettings.builder()
                                               .temperature(0.1f)
                                               .seed(42)
                                               .build())
                        .build(),
                Map.of() // No tools for now
        );
        final var mcpToolBox = toolboxBuilder.apply(objectMapper);
        agent.registerToolbox(mcpToolBox);
        final var response = agent.execute(AgentInput.<String>builder()
                                                   .request("Use tool to add the number 3 and -9")
                                                   .build());
        assertTrue(response.getData().contains("-6"));
        assertNoFailedToolCalls(response);
    }

    private ComposingMCPToolBox buildToolBox(JsonMapper objectMapper) {
        final var params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-everything")
                .build();
        final var transport = new StdioClientTransport(params);

        final var mcpClient = McpClient.sync(transport)
                .build();
        mcpClient.initialize();
        return ComposingMCPToolBox.builder()
                .objectMapper(objectMapper)
                .build()
                .registerMCP("Test MCP", mcpClient, "add");
    }

    private ComposingMCPToolBox buildToolBoxFromFile(JsonMapper objectMapper) {
        final var mcpToolBox = ComposingMCPToolBox.builder()
                .objectMapper(objectMapper)
                .build();
        MCPJsonReader.loadFile(Objects.requireNonNull(getClass().getResource("/mcp.json")).getPath(),
                               mcpToolBox, objectMapper);
        return mcpToolBox;
    }

    private ComposingMCPToolBox buildToolBoxFromFileWithTools(JsonMapper objectMapper) {
        final var mcpToolBox = ComposingMCPToolBox.builder()
                .objectMapper(objectMapper)
                .build();
        MCPJsonReader.loadFile(Objects.requireNonNull(getClass().getResource("/mcp-with-tools.json")).getPath(),
                               mcpToolBox, objectMapper);
        return mcpToolBox;
    }

}