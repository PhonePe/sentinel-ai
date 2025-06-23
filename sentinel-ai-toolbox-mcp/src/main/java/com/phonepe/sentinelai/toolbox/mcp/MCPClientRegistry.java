package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@AllArgsConstructor
@Builder
public class MCPClientRegistry {
    private final Map<String, McpSyncClient> knownClients = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public MCPClientRegistry registerMCPUpstream(String upstream, McpSyncClient client) {
        knownClients.putIfAbsent(upstream, client);
        return this;
    }

    public Optional<McpSyncClient> findClient(String upstream) {
        return Optional.ofNullable(knownClients.get(upstream));
    }
}
