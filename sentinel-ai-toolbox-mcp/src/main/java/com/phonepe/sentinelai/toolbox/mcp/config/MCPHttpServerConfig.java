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

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for HTTP MCP server
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MCPHttpServerConfig extends MCPServerConfig {
    String url; // Endpoint URL of the HTTP server
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
