package com.phonepe.sentinelai.toolbox.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.toolbox.mcp.ComposingMCPToolBox;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Load MCP servers into a {@link ComposingMCPToolBox}
 */
@UtilityClass
public class MCPJsonReader {

    /**
     * Load MCP servers from the provided file path into the toolbox.
     * @param filePath Path to the configuration file
     * @param toolBox Toolbox to register the MCP clients
     * @param objectMapper ObjectMapper to use for serialization/deserialization
     */
    @SneakyThrows
    public static void loadFile(
            final String filePath,
            final ComposingMCPToolBox toolBox,
            final ObjectMapper objectMapper) {
        final var config = objectMapper.readValue(Files.readAllBytes(Path.of(filePath)), MCPConfiguration.class);
        loadServers(config, toolBox, objectMapper);
    }

    /**
     * Load MCP servers from the provided configuration into the toolbox.
     * @param config MCP configuration containing server definitions
     * @param toolBox Toolbox to register the MCP clients
     * @param objectMapper ObjectMapper to use for serialization/deserialization
     */
    public static void loadServers(MCPConfiguration config, ComposingMCPToolBox toolBox, ObjectMapper objectMapper) {
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
                            final var timeout = Objects.requireNonNullElse(sseServerConfig.getTimeout(), 2_000);
                            return HttpClientSseClientTransport.builder(sseServerConfig.getUrl())
                                    .objectMapper(objectMapper)
//                                    .requestBuilder(HttpRequest.newBuilder().timeout(Duration.ofMillis(timeout)))
                                    .build();
                        }
                    });
                    final var client = McpClient.sync(transport).build();
                    client.initialize();
                    toolBox.registerMCP(name, client,
                                        Objects.requireNonNullElseGet(serverConfig.getExposedTools(), Set::of));
                });
    }
}
