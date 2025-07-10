package com.phonepe.sentinelai.toolbox.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.toolbox.mcp.ComposingMCPToolBox;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Load MCP servers into a {@link ComposingMCPToolBox}
 */
@UtilityClass
public class MCPJsonReader {
    @Value
    public static class LoadedMCPData {
        String name;
        MCPServerConfig serverConfig;
        McpSyncClient client;
    }

    /**
     * Load MCP servers from the provided file path into the toolbox.
     *
     * @param filePath     Path to the configuration file
     * @param toolBox      Toolbox to register the MCP clients
     * @param objectMapper ObjectMapper to use for serialization/deserialization
     */
    public static void loadFile(
            final String filePath,
            final ComposingMCPToolBox toolBox,
            final ObjectMapper objectMapper) {
        loadFile(filePath, objectMapper, loadedMCPData ->
                toolBox.registerMCP(loadedMCPData.getName(),
                                    loadedMCPData.getClient(),
                                    Objects.requireNonNullElseGet(loadedMCPData.getServerConfig().getExposedTools(), Set::of)));
    }

    /**
     * Load MCP servers from the provided file path and invoke the handler for each loaded server.
     *
     * @param filePath     Path to the configuration file
     * @param objectMapper ObjectMapper to use for serialization/deserialization
     * @param handler      Consumer to handle each loaded MCP server data
     */
    @SneakyThrows
    public static void loadFile(
            final String filePath,
            final ObjectMapper objectMapper,
            final Consumer<LoadedMCPData> handler){
        final var config = objectMapper.readValue(Files.readAllBytes(Path.of(filePath)), MCPConfiguration.class);
        loadServers(config, objectMapper, handler);
    }

    /**
     * Load MCP servers from the provided configuration into the toolbox.
     *
     * @param config       MCP configuration containing server definitions
     * @param toolBox      Toolbox to register the MCP clients
     * @param objectMapper ObjectMapper to use for serialization/deserialization
     */
    public static void loadServers(MCPConfiguration config, ComposingMCPToolBox toolBox, ObjectMapper objectMapper) {
        loadServers(config, objectMapper, loadedMCPData ->
                toolBox.registerMCP(loadedMCPData.getName(),
                                    loadedMCPData.getClient(),
                                    Objects.requireNonNullElseGet(loadedMCPData.getServerConfig().getExposedTools(), Set::of)));
    }

    /**
     * Load MCP servers from the provided configuration and invoke the handler for each loaded server.
     * @param config       MCP configuration containing server definitions
     * @param objectMapper ObjectMapper to use for serialization/deserialization
     * @param handler      Consumer to handle each loaded MCP server data
     */
    public static void loadServers(
            MCPConfiguration config,
            ObjectMapper objectMapper,
            Consumer<LoadedMCPData> handler) {
        Objects.requireNonNullElseGet(config.getMcpServers(), Map::<String, MCPServerConfig>of)
                .forEach((name, serverConfig) -> {
                    final var transport = serverConfig.accept(new MCPServerConfigVisitor<McpClientTransport>() {
                        @Override
                        public McpClientTransport visit(MCPStdioServerConfig stdioServerConfig) {
                            final var serverParameters = ServerParameters.builder(stdioServerConfig.getCommand())
                                    .args(Objects.requireNonNullElseGet(stdioServerConfig.getArgs(), List::of))
                                    .env(Objects.requireNonNullElseGet(stdioServerConfig.getEnv(), Map::of))
                                    .build();
                            return new StdioClientTransport(serverParameters, objectMapper);
                        }

                        @Override
                        public McpClientTransport visit(MCPSSEServerConfig sseServerConfig) {
                            final var timeout = Objects.requireNonNullElse(sseServerConfig.getTimeout(), 5_000);
                            return HttpClientSseClientTransport.builder(sseServerConfig.getUrl())
                                    .objectMapper(objectMapper)
                                    .customizeClient(builder -> builder.connectTimeout(Duration.ofMillis(timeout)))
                                    .build();
                        }
                    });
                    final var client = McpClient.sync(transport).build();
                    client.initialize();
                    handler.accept(new LoadedMCPData(name, serverConfig, client));
                });
    }
}
