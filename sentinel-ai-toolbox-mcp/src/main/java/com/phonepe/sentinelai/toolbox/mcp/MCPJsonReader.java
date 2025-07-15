package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.toolbox.mcp.config.*;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
    @SneakyThrows
    public static void loadFile(
            final String filePath,
            final ComposingMCPToolBox toolBox,
            final ObjectMapper objectMapper) {
        final var config = objectMapper.readValue(Files.readAllBytes(Path.of(filePath)), MCPConfiguration.class);
        loadServers(config, toolBox);
    }

    /**
     * Load MCP servers from the provided configuration into the toolbox.
     * @param config MCP configuration containing server definitions
     * @param toolBox Toolbox to register the MCP clients
     */
    public static void loadServers(MCPConfiguration config, ComposingMCPToolBox toolBox) {
        loadServers(config, toolBox::registerMCP);
    }

    /**
     * Load MCP servers from the provided configuration into the toolbox.
     *
     * @param config  MCP configuration containing server definitions
     * @param handler BiConsumer to handle each loaded MCP server data
     */
    public static void loadServers(MCPConfiguration config, BiConsumer<String, MCPServerConfig> handler) {
        Objects.requireNonNullElseGet(config.getMcpServers(), Map::<String, MCPServerConfig>of).forEach(handler);
    }

    /**
     * Used by {@link SentinelMCPClient} to create a new MCP client and provide sampling callback
     * @param objectMapper ObjectMapper to use for serialization/deserialization
     * @param name Name of the MCP client
     * @param serverConfig MCP server configuration to connect to
     * @param samplingHandler Function to handle sampling requests from MCP servers
     * @return A structure containing the name, server configuration, and the created MCP client
     */
    public static LoadedMCPData createMcpClient(
            ObjectMapper objectMapper,
            String name,
            MCPServerConfig serverConfig,
            Function<McpSchema.CreateMessageRequest, McpSchema.CreateMessageResult> samplingHandler) {
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
        final var client = McpClient.sync(transport).sampling(samplingHandler).build();
        client.initialize();
        return new LoadedMCPData(name, serverConfig, client);
    }

}
