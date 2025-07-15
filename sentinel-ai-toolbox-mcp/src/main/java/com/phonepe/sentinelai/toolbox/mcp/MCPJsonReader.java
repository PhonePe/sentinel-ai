package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPConfiguration;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPServerConfig;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Load MCP servers into a {@link ComposingMCPToolBox}
 */
@UtilityClass
public class MCPJsonReader {

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
     *
     * @param config  MCP configuration containing server definitions
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

}
