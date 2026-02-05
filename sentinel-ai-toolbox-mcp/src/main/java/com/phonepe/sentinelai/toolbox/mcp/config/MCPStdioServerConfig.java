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
    public MCPStdioServerConfig(Set<String> exposedTools, @NonNull String command, List<String> args,
            Map<String, String> env) {
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
