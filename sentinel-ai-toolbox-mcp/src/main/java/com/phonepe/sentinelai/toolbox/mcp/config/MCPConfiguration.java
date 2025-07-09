package com.phonepe.sentinelai.toolbox.mcp.config;

import lombok.NonNull;
import lombok.Value;

import java.util.Map;

@Value
public class MCPConfiguration {

    @NonNull
    Map<String, MCPServerConfig> mcpServers;
}

