package com.phonepe.sentinelai.toolbox.mcp.config;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/**
 * Config for SSE based MCP server
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MCPSSEServerConfig extends MCPServerConfig {
    String url; // Endpoint URL of the server (for http)
    Integer timeout; //Timeout in millis

    @Builder
    @Jacksonized
    public MCPSSEServerConfig(Set<String> exposedTools, String url, Integer timeout) {
        super(MCPServerType.SSE, exposedTools);
        this.url = url;
        this.timeout = timeout;
    }

    @Override
    public <T> T accept(MCPServerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
