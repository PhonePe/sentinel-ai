package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 *
 */
@Slf4j
public class MCPToolBox implements ToolBox {
    private final SentinelMCPClient mcpClient;

    @Builder
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
}
