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

package com.phonepe.sentinelai.toolbox.mcp;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link SentinelMCPClient}
 */
class SentinelMCPClientTest {

    @Test
    void test() {
        final var objectMapper = JsonUtils.createMapper();
        final var params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-everything@2025.12.18")
                .build();
        final var transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(objectMapper));

        final var mcpClient = McpClient.sync(transport)
                .build();
        mcpClient.initialize();
        final var mcpToolBox = new MCPToolBox("Test MCP", mcpClient, objectMapper, Set.of());

        final var allTools = Set.of("test_mcp_annotated_message",
                                                   "test_mcp_add",
                                                   "test_mcp_get_resource_reference",
                                                   "test_mcp_long_running_operation",
                                                   "test_mcp_get_tiny_image",
                                                   "test_mcp_echo",
                                                   "test_mcp_print_env",
                                                   "test_mcp_sample_llm");
        final var allToolsSize = mcpToolBox.tools().size();
        assertTrue(mcpToolBox.tools().keySet().containsAll(allTools));

        //Now filter out and keep only 2 tools
        mcpToolBox.exposeTools("add", "echo");
        final var filteredTools = mcpToolBox.tools();
        assertEquals(2, filteredTools.size());
        assertEquals(Set.of("test_mcp_add", "test_mcp_echo"), filteredTools.keySet());

        mcpToolBox.exposeAllTools();
        assertEquals(allToolsSize, mcpToolBox.tools().size());
    }
}