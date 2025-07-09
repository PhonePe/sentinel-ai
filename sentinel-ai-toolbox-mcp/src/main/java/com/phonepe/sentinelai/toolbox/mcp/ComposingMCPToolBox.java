package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPJsonReader;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * ComposingMCPToolBox is a tool box that composes multiple MCP clients.
 * It allows for dynamic selection of tools from different MCP clients.
 */
@Slf4j
public class ComposingMCPToolBox implements ToolBox {

    private final String name;

    private final ObjectMapper objectMapper;

    private final Map<String, SentinelMCPClient> mcpClients = new ConcurrentHashMap<>();

    /**
     * Create a new ComposingMCPToolBox with the provided ObjectMapper and name.
     * If name is not provided, a random UUID will be used as the name.
     *
     * @param objectMapper The ObjectMapper to use for serialization/deserialization
     * @param name         Name of the toolbox
     * @param mcpJsonPath  Path to the MCP JSON configuration file
     */
    @Builder
    public ComposingMCPToolBox(@NonNull ObjectMapper objectMapper, String name, String mcpJsonPath) {
        this.objectMapper = objectMapper;
        this.name = Objects.requireNonNullElseGet(
                name,
                () -> "composing-mcp-toolbox-%s".formatted(UUID.randomUUID().toString()));
        if(!Strings.isNullOrEmpty(mcpJsonPath)) {
            MCPJsonReader.loadFile(mcpJsonPath, this, objectMapper);
        }
    }

    /**
     * Register an MCP client to the toolbox. Name for client will be what is set as {@link SentinelMCPClient#getName()}
     *
     * @param name        Name of the client
     * @param client      The MCP client to use for communications
     * @param exposedTool Tools exposed from the MCP server
     * @return itself
     */
    public ComposingMCPToolBox registerMCP(
            @NonNull String name,
            @NonNull McpSyncClient client,
            String... exposedTool) {
        return this.registerMCP(name, client, Arrays.asList(exposedTool));
    }

    /**
     * Register an MCP client to the toolbox. Name for client will be what is set as {@link SentinelMCPClient#getName()}
     *
     * @param name         Name of the client
     * @param client       The MCP client to use for communications
     * @param exposedTools Tools exposed from the MCP server. Send an empty set to expose all tools.
     * @return itself
     */
    public ComposingMCPToolBox registerMCP(
            @NonNull String name,
            @NonNull McpSyncClient client,
            @NonNull Collection<String> exposedTools) {
        mcpClients.put(name,
                       new SentinelMCPClient(name, client, objectMapper, Set.copyOf(exposedTools)));
        return this;
    }

    /**
     * Expose the specified tools from the provided mcp server
     * @param name Name of the server
     * @param tools Tools to be exposed
     * @return this
     */
    public ComposingMCPToolBox exposeTools(@NonNull String name, String... tools) {
        return this.exposeTools(name, Arrays.asList(tools));
    }

    /**
     * Expose the specified tools from the provided mcp server
     * @param name Name of the server
     * @param tools Tools to be exposed
     * @return this
     */
    public ComposingMCPToolBox exposeTools(@NonNull String name, @NonNull Collection<String> tools) {
        final var client = mcpClients.get(name);
        if(null != client) {
            client.exposeTools(tools);
        }
        return this;
    }

    /**
     * Expose all tools from the specified MCP servers. Useful when filters need to be removed.
     * @param name Name of the server
     * @return this
     */
    public ComposingMCPToolBox exposeAllTools(@NonNull String name) {
        final var client = mcpClients.get(name);
        if(null != client) {
            client.exposeAllTools();
        }
        return this;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        log.debug("Composing tools from MCP clients: {}", mcpClients.keySet());
        final var relevantTools = Map.copyOf(mcpClients.values()
                                                     .stream()
                                                     .flatMap(client -> client.tools()
                                                             .entrySet()
                                                             .stream())
                                                     .collect(toUnmodifiableMap(Map.Entry::getKey,
                                                                                Map.Entry::getValue)));
        log.debug("Found {} tools in ComposingMCPToolBox [{}]: {}", relevantTools.size(), name, relevantTools.keySet());
        return relevantTools;
    }
}
