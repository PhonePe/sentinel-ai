package com.phonepe.sentinelai.toolbox.mcp.config;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for STDIO MCP server
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MCPStdioServerConfig extends MCPServerConfig {
    String command; // Command to launch the server
    List<String> args; // Arguments for the command
    Map<String, String> env; // Environment variables

    @Builder
    @Jacksonized
    public MCPStdioServerConfig(Set<String> exposedTools, @NonNull String command, List<String> args, Map<String, String> env) {
        super(MCPServerType.STDIO, exposedTools);
        this.command = command;
        this.args = args;
        this.env = env;
    }

    @Override
    public <T> T accept(MCPServerConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
