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

package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.models.ConfiguredModel;
import com.phonepe.sentinelai.models.TestStubs;
import com.phonepe.sentinelai.models.openai.ChatCompletionsProtocol;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPConfiguration;

import lombok.NonNull;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.phonepe.sentinelai.core.utils.TestUtils.assertNoFailedToolCalls;
import static com.phonepe.sentinelai.core.utils.TestUtils.ensureOutputGenerated;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@WireMockTest
class ComposingMCPToolBoxIntegrationTest {
    private static class CompositeMCPTestAgent extends Agent<String, String, CompositeMCPTestAgent> {

        public CompositeMCPTestAgent(@NonNull AgentSetup setup,
                                     Map<String, ExecutableTool> knownTools) {
            super(String.class,
                  """
                          Respond to user's queries. Use the provided tools to get the correct information.
                          """,
                  setup,
                  List.of(),
                  knownTools);
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
        TestStubs.setupMocks(2, "tc", getClass());
        final var httpClient = new OkHttpClient.Builder().build();
        final var objectMapper = JsonUtils.createMapper();
        final var model = ConfiguredModel.builder()
                .modelName("gpt-4o")
                .provider(Provider.builder()
                        .baseUrl(wiremock.getHttpBaseUrl())
                        .protocol(new ChatCompletionsProtocol())
                        .auth(HeaderAuth.bearer("test-key"))
                        .build())
                .httpClient(httpClient)
                .build();

        final var agent = new CompositeMCPTestAgent(AgentSetup.builder()
                .mapper(objectMapper)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0.1f)
                        .seed(42)
                        .build())
                .build(), Map.of() // No tools for now
        );
        final var mcpToolBox = toolboxBuilder.apply(objectMapper);
        agent.registerToolbox(mcpToolBox);
        final var response = agent.execute(AgentInput.<String>builder()
                .request("Use tool to add the number 3 and -9")
                .build());
        assertTrue(response.getData().contains("-6"));
        assertNoFailedToolCalls(response);
        ensureOutputGenerated(response);
    }

    private ComposingMCPToolBox buildToolBox(JsonMapper objectMapper) {
        final var params = ServerParameters.builder("npx")
                .args("-y",
                      "@modelcontextprotocol/server-everything@2025.12.18")
                .build();
        final var transport = new StdioClientTransport(params,
                                                       new JacksonMcpJsonMapper(objectMapper));

        final var mcpClient = McpClient.sync(transport).build();
        mcpClient.initialize();
        return ComposingMCPToolBox.buildEmpty()
                .objectMapper(objectMapper)
                .build()
                .registerExistingMCP("Test MCP", mcpClient, "add");
    }

    private ComposingMCPToolBox buildToolBoxFromFile(JsonMapper objectMapper) {
        return ComposingMCPToolBox.buildFromFile()
                .objectMapper(objectMapper)
                .mcpJsonFilePath(Objects.requireNonNull(getClass().getResource(
                                                                               "/mcp.json"))
                        .getPath())
                .build();
    }

    @SneakyThrows
    private ComposingMCPToolBox buildToolBoxFromFileWithTools(JsonMapper objectMapper) {
        final var fileContents = Files.readAllBytes(Paths.get(Objects
                .requireNonNull(getClass().getResource("/mcp-with-tools.json"))
                .getPath()));
        return ComposingMCPToolBox.buildFromConfig()
                .objectMapper(objectMapper)
                .configuration(objectMapper.readValue(fileContents,
                                                      MCPConfiguration.class))
                .build();
    }

}
