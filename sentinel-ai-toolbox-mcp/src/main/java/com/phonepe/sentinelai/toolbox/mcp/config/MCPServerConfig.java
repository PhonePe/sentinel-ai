/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        @JsonSubTypes.Type(name = MCPServerType.Values.HTTP_TEXT, value = MCPHttpServerConfig.class),
})
@Data
@RequiredArgsConstructor
public abstract class MCPServerConfig {

    private final MCPServerType type; // For example, "stdio" or "http"
    private final Set<String> exposedTools;

    public abstract <T> T accept(final MCPServerConfigVisitor<T> visitor);
}
