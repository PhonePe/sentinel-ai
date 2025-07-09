package com.phonepe.sentinelai.toolbox.mcp.config;

/**
 * To implement subclass specific behaviour for {@link MCPServerConfig}
 */
public interface MCPServerConfigVisitor<T> {
    T visit(MCPStdioServerConfig stdioServerConfig);

    T visit(MCPSSEServerConfig sseServerConfig);
}
