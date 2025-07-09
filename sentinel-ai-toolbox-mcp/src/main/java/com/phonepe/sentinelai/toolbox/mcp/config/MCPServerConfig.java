package com.phonepe.sentinelai.toolbox.mcp.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Config for specific MCP server
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = MCPServerType.Values.STDIO_TEXT, value = MCPStdioServerConfig.class),
        @JsonSubTypes.Type(name = MCPServerType.Values.SSE_TEXT, value = MCPSSEServerConfig.class),
})
@Data
@RequiredArgsConstructor
public abstract class MCPServerConfig {

    private final MCPServerType type; // For example, "stdio" or "http"
    private final Set<String> exposedTools;

    public abstract <T> T accept(final MCPServerConfigVisitor<T> visitor);
}
