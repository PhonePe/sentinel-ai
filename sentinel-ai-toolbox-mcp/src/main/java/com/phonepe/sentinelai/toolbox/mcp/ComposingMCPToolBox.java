package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPConfiguration;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPServerConfig;
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
     * @param objectMapper  The ObjectMapper to use for serialization/deserialization
     * @param configuration MCP configuration containing server definitions
     * @param name          Name of the toolbox. If not provided, a random UUID will be used as the name.
     */
    @Builder(builderMethodName = "buildFromConfig", builderClassName = "BuilderFromConfig")
    public ComposingMCPToolBox(
            @NonNull ObjectMapper objectMapper,
            @NonNull MCPConfiguration configuration,
            String name) {
        this.objectMapper = objectMapper;
        this.name = toolBoxName(name);
        MCPJsonReader.loadServers(configuration, this);
    }

    /**
     * Create a new ComposingMCPToolBox with the provided ObjectMapper and name.
     * If name is not provided, a random UUID will be used as the name.
     *
     * @param objectMapper    The ObjectMapper to use for serialization/deserialization
     * @param mcpJsonFilePath Path to the MCP JSON configuration file
     * @param name            Name of the toolbox. If not provided, a random UUID will be used as the name.
     */
    @Builder(builderMethodName = "buildFromFile", builderClassName = "BuilderFromFile")
    public ComposingMCPToolBox(@NonNull ObjectMapper objectMapper, @NonNull String mcpJsonFilePath, String name) {
        this.objectMapper = objectMapper;
        this.name = toolBoxName(name);
        if (!Strings.isNullOrEmpty(mcpJsonFilePath)) {
            MCPJsonReader.loadFile(mcpJsonFilePath, this, objectMapper);
        }
    }

    /**
     * Build a new ComposingMCPToolBox with the provided ObjectMapper and name. This will have no MCP servers
     * registered.
     * Use {@link #registerMCP(String, MCPServerConfig)} or
     * {@link #registerExistingMCP(String, McpSyncClient, String...)} to register MCP clients later. You can also use
     * {@link MCPJsonReader#loadFile(String, ComposingMCPToolBox, ObjectMapper)} to load MCP servers from a file.
     *
     * @param objectMapper The ObjectMapper to use for serialization/deserialization
     * @param name         Name of the toolbox. If not provided, a random UUID will be used as the name.
     */
    @Builder(builderMethodName = "buildEmpty", builderClassName = "EmptyBuilder")
    public ComposingMCPToolBox(@NonNull ObjectMapper objectMapper, String name) {
        this.objectMapper = objectMapper;
        this.name = toolBoxName(name);
    }

    /**
     * Register an MCP client to the toolbox. Name for client will be what is set as
     * {@link SentinelMCPClient#getName()}.
     * Please note that Sentinel will not be able to handle sampling calls in the usual manner. However, whatever has
     * been set as the sampling callback  when creating the MCP client will obviously execute.
     *
     * @param name        Name of the client
     * @param client      The MCP client to use for communications
     * @param exposedTool Tools exposed from the MCP server
     * @return itself
     */
    public ComposingMCPToolBox registerExistingMCP(
            @NonNull String name,
            @NonNull McpSyncClient client,
            String... exposedTool) {
        return this.registerExistingMCP(name, client, Arrays.asList(exposedTool));
    }

    /**
     * Register an MCP client to the toolbox. Name for client will be what is set as {@link SentinelMCPClient#getName()}
     *
     * @param name         Name of the client
     * @param client       The MCP client to use for communications
     * @param exposedTools Tools exposed from the MCP server. Send an empty set to expose all tools.
     * @return itself
     */
    public ComposingMCPToolBox registerExistingMCP(
            @NonNull String name,
            @NonNull McpSyncClient client,
            @NonNull Collection<String> exposedTools) {
        mcpClients.put(name,
                       new SentinelMCPClient(name, client, objectMapper, Set.copyOf(exposedTools)));
        return this;
    }

    /**
     * Register a new MCP server to the toolbox.
     *
     * @param name         Name of the MCP server
     * @param serverConfig Configuration for the MCP server
     * @return itself
     */
    public ComposingMCPToolBox registerMCP(
            @NonNull String name,
            @NonNull MCPServerConfig serverConfig) {
        mcpClients.put(name,
                       new SentinelMCPClient(name, serverConfig, objectMapper, serverConfig.getExposedTools()));
        return this;
    }

    /**
     * Expose the specified tools from the provided mcp server
     *
     * @param name  Name of the server
     * @param tools Tools to be exposed
     * @return this
     */
    public ComposingMCPToolBox exposeTools(@NonNull String name, String... tools) {
        return this.exposeTools(name, Arrays.asList(tools));
    }

    /**
     * Expose the specified tools from the provided mcp server
     *
     * @param name  Name of the server
     * @param tools Tools to be exposed
     * @return this
     */
    public ComposingMCPToolBox exposeTools(@NonNull String name, @NonNull Collection<String> tools) {
        final var client = mcpClients.get(name);
        if (null != client) {
            client.exposeTools(tools);
        }
        return this;
    }

    /**
     * Expose all tools from the specified MCP servers. Useful when filters need to be removed.
     *
     * @param name Name of the server
     * @return this
     */
    public ComposingMCPToolBox exposeAllTools(@NonNull String name) {
        final var client = mcpClients.get(name);
        if (null != client) {
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

    private static String toolBoxName(String name) {
        return Objects.requireNonNullElseGet(
                name,
                () -> "composing-mcp-toolbox-%s".formatted(UUID.randomUUID().toString()));
    }
}
