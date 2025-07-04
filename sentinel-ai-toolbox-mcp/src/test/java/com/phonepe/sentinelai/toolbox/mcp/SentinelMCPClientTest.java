package com.phonepe.sentinelai.toolbox.mcp;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link SentinelMCPClient}
 */
class SentinelMCPClientTest {

    @Test
    void test() {
        final var objectMapper = JsonUtils.createMapper();
        final var params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-everything")
                .build();
        final var transport = new StdioClientTransport(params);

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
        assertEquals(allTools, mcpToolBox.tools().keySet());

        //Now filter out and keep only 2 tools
        mcpToolBox.exposeTools("add", "echo");
        final var filteredTools = mcpToolBox.tools();
        assertEquals(2, filteredTools.size());
        assertEquals(Set.of("test_mcp_add", "test_mcp_echo"), filteredTools.keySet());

        mcpToolBox.exposeAllTools();
        assertEquals(allTools, mcpToolBox.tools().keySet());
    }
}