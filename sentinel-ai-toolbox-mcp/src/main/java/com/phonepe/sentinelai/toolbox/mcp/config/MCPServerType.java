package com.phonepe.sentinelai.toolbox.mcp.config;

import lombok.Getter;
import lombok.experimental.UtilityClass;

/**
 * Types of MCP servers supported
 */
@Getter
public enum MCPServerType {

    STDIO(Values.STDIO_TEXT),
    SSE(Values.SSE_TEXT),
    ;
    private final String value;

    MCPServerType(String value) {
        this.value = value;
    }

    @UtilityClass
    public static final class Values {
        public static final String STDIO_TEXT = "stdio";
        public static final String SSE_TEXT = "sse";
    }
}
