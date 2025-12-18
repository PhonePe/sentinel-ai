package com.phonepe.sentinelai.toolbox.mcp.config;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for STDIO MCP server
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MCPHttpServerConfig extends MCPServerConfig {
    String url; // Command to launch the server
    Map<String, String> headers; // Headers for the HTTP requests
    Integer timeout; //Timeout in millis (default 5 seconds)

    @Builder
    @Jacksonized
    public MCPHttpServerConfig(Set<String> exposedTools,
                               @NonNull String url,
                               Map<String, String> headers,
                               Integer timeout) {
        super(MCPServerType.HTTP, exposedTools);
        this.url = url;
        this.headers = headers;
        this.timeout = timeout;
    }

    @Override
    public <T> T accept(MCPServerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
