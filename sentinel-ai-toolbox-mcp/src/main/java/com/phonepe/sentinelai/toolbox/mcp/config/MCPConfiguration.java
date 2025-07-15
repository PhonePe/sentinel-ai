package com.phonepe.sentinelai.toolbox.mcp.config;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Value
@Builder
@Jacksonized
public class MCPConfiguration {

    @Singular
    @NonNull
    Map<String, MCPServerConfig> mcpServers;
}

