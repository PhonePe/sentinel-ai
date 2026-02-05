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

package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpSyncClient;

import com.phonepe.sentinelai.toolbox.mcp.MCPJsonReader;
import com.phonepe.sentinelai.toolbox.mcp.MCPToolBox;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPConfiguration;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPServerConfig;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A factory to create instances of {@link MCPToolBox} from the client provider. We do not apply tool filters here,
 * that is done in the {@link ConfiguredAgentFactory} where we can apply filters based on the agent configuration.
 */
@Slf4j
public class MCPToolBoxFactory {
    @NonNull
    private final ObjectMapper objectMapper;

    private final Function<String, Optional<McpSyncClient>> clientProvider;
    private final Map<String, MCPServerConfig> knownConfigs = new ConcurrentHashMap<>();
    private final Map<String, McpSyncClient> knownClients = new ConcurrentHashMap<>();

    @Builder
    public MCPToolBoxFactory(@NonNull ObjectMapper objectMapper,
            Function<String, Optional<McpSyncClient>> clientProvider) {
        this.objectMapper = objectMapper;
        this.clientProvider = Objects.requireNonNullElse(clientProvider, upstream -> Optional.empty());
    }

    /**
     * Create an MCPToolBox for the given upstream if the upstream is known. If not call the clientProvider to get
     * a McpSyncClient for the upstream and build an MCPToolBox from that client. If the clientProvider does not
     * provide a client, return an empty Optional.
     *
     * @param upstream Name of the MCP server
     * @return An Optional containing the MCPToolBox if the upstream is known, otherwise an empty Optional.
     */
    public Optional<MCPToolBox> create(String upstream) {
        final var tbFromServerConfig = Optional.ofNullable(knownConfigs.get(upstream))
                .map(mcpServerConfig -> new MCPToolBox(upstream, objectMapper, mcpServerConfig));
        if (tbFromServerConfig.isPresent()) {
            log.debug("Found MCPServerConfig for upstream: {}", upstream);
            return tbFromServerConfig;
        }
        final var tbFromKnownClient = Optional.ofNullable(knownClients.get(upstream)).map(client -> {
            log.debug("Found known McpSyncClient for upstream: {}", upstream);
            return new MCPToolBox(upstream, client, objectMapper, Set.of());
        });
        if (tbFromKnownClient.isPresent()) {
            return tbFromKnownClient;
        }
        log.debug("No MCPServerConfig or Client found for upstream: {}. Falling back to client provider.", upstream);
        final var tbClientProvided = clientProvider.apply(upstream).map(client -> {
            log.debug("Client provider provided a client for upstream: {}", upstream);
            return new MCPToolBox(upstream, client, objectMapper, Set.of());
        });
        if (tbClientProvided.isEmpty()) {
            log.error("No toolbox could be constructed for mcp server name: {}. Returning empty.", upstream);
        }
        return tbClientProvided;
    }

    /**
     * Load MCP server configs from the given byte array
     *
     * @param contents Contents of the server.json file
     * @return this factory with loaded MCPToolBox instances
     */
    @SneakyThrows
    public MCPToolBoxFactory loadFromContent(byte[] contents) {
        final var config = objectMapper.readValue(contents, MCPConfiguration.class);
        MCPJsonReader.loadServers(config, this::registerMCPServerConfig);
        return this;
    }

    /**
     * Load MCP server configs from server.json file
     *
     * @param serverJsonPath Path to server.json file
     * @return this factory with loaded MCPToolBox instances
     */
    @SneakyThrows
    public MCPToolBoxFactory loadFromFile(String serverJsonPath) {
        final var contents = Files.readAllBytes(Path.of(serverJsonPath));
        return loadFromContent(contents);
    }

    /**
     * Add an MCP server config for the given upstream. If a config already exists for the upstream, it is not
     * overwritten.
     *
     * @param upstream Name of the MCP server
     * @param config   MCP server configuration
     * @return this factory with the added MCP server config
     */
    public MCPToolBoxFactory registerMCPServerConfig(String upstream, MCPServerConfig config) {
        knownConfigs.putIfAbsent(upstream, config);
        return this;
    }

    /**
     * Add an MCP client for the given upstream. If a client already exists for the upstream, it is not
     * overwritten.
     *
     * @param upstream Name of the MCP server
     * @param client   MCP client
     * @return this factory with the added MCP client
     */
    public MCPToolBoxFactory registerMcpClient(String upstream, McpSyncClient client) {
        knownClients.putIfAbsent(upstream, client);
        return this;
    }
}
