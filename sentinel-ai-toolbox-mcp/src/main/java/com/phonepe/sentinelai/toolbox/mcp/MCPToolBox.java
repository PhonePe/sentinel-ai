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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPServerConfig;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * MCPToolBox is a tool box that uses the Model Context Protocol (MCP) to manage tools.
 */
@Slf4j
public class MCPToolBox implements ToolBox {
    private final SentinelMCPClient mcpClient;

    @Builder(builderMethodName = "buildFromConfig")
    public MCPToolBox(
            @NonNull final String name,
            @NonNull final ObjectMapper mapper,
            @NonNull final MCPServerConfig mcpServerConfig) {
        this.mcpClient = new SentinelMCPClient(name, mcpServerConfig, mapper, mcpServerConfig.getExposedTools());
    }

    @Builder(builderMethodName = "builder")
    public MCPToolBox(
            @NonNull String name,
            @NonNull McpSyncClient mcpClient,
            @NonNull ObjectMapper mapper,
            Set<String> exposedTools) {
        this.mcpClient = new SentinelMCPClient(name, mcpClient, mapper, exposedTools);
    }

    public MCPToolBox exposeTools(String... toolId) {
        return this.exposeTools(Arrays.asList(toolId));
    }

    public MCPToolBox exposeTools(Collection<String> toolIds) {
        this.mcpClient.exposeTools(toolIds);
        return this;
    }

    public MCPToolBox exposeAllTools() {
        this.mcpClient.exposeAllTools();
        return this;
    }

    @Override
    public String name() {
        return mcpClient.getName();
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        return mcpClient.tools();
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void onToolBoxRegistrationCompleted(A agent) {
        this.mcpClient.onRegistrationCompleted(agent);
        log.info("MCP ToolBox {} registered for agent: {}", name(), agent.name());
    }
}
