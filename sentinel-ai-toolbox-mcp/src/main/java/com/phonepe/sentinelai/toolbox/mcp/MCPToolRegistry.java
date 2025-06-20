package com.phonepe.sentinelai.toolbox.mcp;

import io.modelcontextprotocol.client.McpSyncClient;

/**
 *
 */
public interface MCPToolRegistry {
    default void registerMCPUpstream(String upstream, McpSyncClient client) {

    }
}
