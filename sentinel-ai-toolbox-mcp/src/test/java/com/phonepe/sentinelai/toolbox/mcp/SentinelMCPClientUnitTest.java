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

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SentinelMCPClient} using a mock {@link McpSyncClient}.
 */
@ExtendWith(MockitoExtension.class)
class SentinelMCPClientUnitTest {

    private static final String CLIENT_NAME = "test-client";

    @Mock
    private McpSyncClient mockMcpClient;

    private SentinelMCPClient client;

    @BeforeEach
    void setUp() {
        client = new SentinelMCPClient(CLIENT_NAME,
                                       mockMcpClient,
                                       JsonUtils.createMapper(),
                                       Set.of());
    }

    @Test
    void testClose() {
        client.close();
        verify(mockMcpClient).close();
    }

    @Test
    void testConvertFromSamplingResourceMessage() {
        final var textResource = new McpSchema.TextResourceContents("file:///doc.txt", "text/plain", "doc content");
        final var embeddedResource = new McpSchema.EmbeddedResource(null, textResource);
        final var messages = List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER, embeddedResource));

        final var result = client.convertFromSamplingToAgentMessages("session-1", "run-1", messages);

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof GenericResource);
    }

    @Test
    void testConvertFromSamplingTextMessage() {
        final var messages = List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER,
                                                                   new McpSchema.TextContent("hello world")),
                                     new McpSchema.SamplingMessage(McpSchema.Role.ASSISTANT,
                                                                   new McpSchema.TextContent("response")));

        final var result = client.convertFromSamplingToAgentMessages("session-1", "run-1", messages);

        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof GenericText);
        assertTrue(result.get(1) instanceof GenericText);
    }

    @Test
    @SneakyThrows
    void testConvertResourceResponseBlobResource() {
        final var blobResource = new McpSchema.BlobResourceContents("file:///image.png",
                                                                    "image/png",
                                                                    "base64encodeddata");
        final var embeddedResource = new McpSchema.EmbeddedResource(null, blobResource);
        final var message = new McpSchema.SamplingMessage(McpSchema.Role.ASSISTANT, embeddedResource);

        final var result = client.convertResourceResponse("session-1", "run-1", message);

        assertNotNull(result);
        assertTrue(result instanceof GenericResource);
        final var resource = (GenericResource) result;
        assertEquals(GenericResource.ResourceType.BLOB, resource.getResourceType());
        assertEquals("file:///image.png", resource.getUri());
    }

    @Test
    @SneakyThrows
    void testConvertResourceResponseTextResource() {
        final var textResource = new McpSchema.TextResourceContents("file:///test.txt", "text/plain", "file content");
        final var embeddedResource = new McpSchema.EmbeddedResource(null, textResource);
        final var message = new McpSchema.SamplingMessage(McpSchema.Role.USER, embeddedResource);

        final var result = client.convertResourceResponse("session-1", "run-1", message);

        assertNotNull(result);
        assertTrue(result instanceof GenericResource);
        final var resource = (GenericResource) result;
        assertEquals(GenericResource.ResourceType.TEXT, resource.getResourceType());
        assertEquals("file:///test.txt", resource.getUri());
    }

    @Test
    void testExposeAllToolsClearsFilter() {
        final var tools = buildListToolsResult("add", "echo");
        when(mockMcpClient.listTools()).thenReturn(tools);

        client.exposeTools("add");
        assertEquals(1, client.tools().size());

        client.exposeAllTools();
        assertEquals(2, client.tools().size());
    }

    @Test
    void testExposeToolsCollectionWithNull() {
        client.exposeTools((java.util.Collection<String>) null);
        final var toolsResult = buildListToolsResult("add");
        when(mockMcpClient.listTools()).thenReturn(toolsResult);

        assertEquals(1, client.tools().size());
    }

    @Test
    void testExposeToolsVarargs() {
        client.exposeTools("add", "echo");
        final var tools = buildListToolsResult("add", "echo", "multiply");
        when(mockMcpClient.listTools()).thenReturn(tools);

        final var result = client.tools();
        assertEquals(2, result.size());
    }

    @Test
    void testGetName() {
        assertEquals(CLIENT_NAME, client.getName());
    }

    @Test
    void testHandleSamplingRequestNoAgent() {
        final var request = McpSchema.CreateMessageRequest.builder()
                .messages(List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER,
                                                                new McpSchema.TextContent("hello"))))
                .systemPrompt("You are helpful")
                .maxTokens(100)
                .build();

        final var result = client.handleSamplingRequest(request);

        assertNotNull(result);
        assertEquals(McpSchema.Role.ASSISTANT, result.role());
        assertTrue(result.content().toString().contains("No agent"));
    }

    @Test
    void testHandleSamplingRequestNoAgentIndirect() {
        final var toolsResult = buildListToolsResult("sample_llm");
        when(mockMcpClient.listTools()).thenReturn(toolsResult);

        final var result = client.tools();
        assertFalse(result.isEmpty());
    }

    @Test
    void testOnRegistrationCompleted() {
        client.onRegistrationCompleted(null);
    }

    @Test
    void testRunToolErrorResponse() {
        final var tools = buildListToolsResult("add");
        when(mockMcpClient.listTools()).thenReturn(tools);
        client.tools();

        final var callResult = McpSchema.CallToolResult.builder()
                .addTextContent("something went wrong")
                .isError(true)
                .build();
        when(mockMcpClient.callTool(any())).thenReturn(callResult);

        final var toolId = buildToolId("add");
        final var response = client.runTool(null, toolId, "{}");

        assertNotNull(response);
        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, response.error());
    }

    @Test
    void testRunToolExceptionPath() {
        final var tools = buildListToolsResult("add");
        when(mockMcpClient.listTools()).thenReturn(tools);
        client.tools();

        when(mockMcpClient.callTool(any())).thenThrow(new RuntimeException("connection failed"));

        final var toolId = buildToolId("add");
        final var response = client.runTool(null, toolId, "{}");

        assertNotNull(response);
        assertEquals(ErrorType.GENERIC_MODEL_CALL_FAILURE, response.error());
        assertTrue(response.response().toString().contains("Error processing request"));
    }

    @Test
    void testRunToolInvalidToolId() {
        final var tools = buildListToolsResult("add");
        when(mockMcpClient.listTools()).thenReturn(tools);
        client.tools();

        final var response = client.runTool(null, "nonexistent_tool", "{}");

        assertNotNull(response);
        assertEquals(ErrorType.TOOL_CALL_PERMANENT_FAILURE, response.error());
        assertTrue(response.response().toString().contains("Invalid tool"));
    }

    @Test
    void testRunToolSuccess() {
        final var tools = buildListToolsResult("add");
        when(mockMcpClient.listTools()).thenReturn(tools);
        client.tools();

        final var callResult = McpSchema.CallToolResult.builder()
                .addTextContent("42")
                .isError(false)
                .build();
        when(mockMcpClient.callTool(any())).thenReturn(callResult);

        final var toolId = buildToolId("add");
        final var response = client.runTool(null, toolId, "{\"a\":1,\"b\":2}");

        assertNotNull(response);
        assertEquals(ErrorType.SUCCESS, response.error());
    }

    @Test
    void testRunToolWithContext() {
        final var tools = buildListToolsResult("echo");
        when(mockMcpClient.listTools()).thenReturn(tools);
        client.tools();

        final var callResult = McpSchema.CallToolResult.builder()
                .addTextContent("echoed")
                .isError(false)
                .build();
        when(mockMcpClient.callTool(any())).thenReturn(callResult);

        final var context = new AgentRunContext<>("run-1", "hello", null, null, null, null, null);
        final var toolId = buildToolId("echo");
        final var response = client.runTool(context, toolId, "{\"message\":\"hello\"}");

        assertEquals(ErrorType.SUCCESS, response.error());
    }

    @Test
    void testToStopReasonLengthExceeded() {
        assertEquals(McpSchema.CreateMessageResult.StopReason.MAX_TOKENS,
                     client.toStopReason(ErrorType.LENGTH_EXCEEDED));
        assertEquals(McpSchema.CreateMessageResult.StopReason.END_TURN,
                     client.toStopReason(ErrorType.SUCCESS));
    }

    @Test
    void testToolsFilterByExposedToolName() {
        client.exposeTools("echo");
        final var tools = buildListToolsResult("add", "echo", "multiply");
        when(mockMcpClient.listTools()).thenReturn(tools);

        final var result = client.tools();
        assertEquals(1, result.size());

        final var tool = result.values().iterator().next();
        assertEquals("echo", tool.getToolDefinition().getName());
    }

    @Test
    void testToolsReturnsCachedResultOnSecondCall() {
        final var tools = buildListToolsResult("add");
        when(mockMcpClient.listTools()).thenReturn(tools);

        client.tools();
        client.tools();

        verify(mockMcpClient).listTools();
    }

    @Test
    void testToolsWithNoFilter() {
        final var tools = buildListToolsResult("add", "echo", "multiply");
        when(mockMcpClient.listTools()).thenReturn(tools);

        final var result = client.tools();
        assertEquals(3, result.size());
        assertNotNull(result);
    }

    @Test
    void testTranslateRole() {
        assertEquals(AgentGenericMessage.Role.USER, client.translateRole(McpSchema.Role.USER));
        assertEquals(AgentGenericMessage.Role.ASSISTANT, client.translateRole(McpSchema.Role.ASSISTANT));
    }

    private McpSchema.ListToolsResult buildListToolsResult(String... toolNames) {
        final var toolList = new java.util.ArrayList<McpSchema.Tool>();
        for (final var name : toolNames) {
            toolList.add(McpSchema.Tool.builder()
                    .name(name)
                    .description("Tool: " + name)
                    .inputSchema(new McpSchema.JsonSchema("object",
                                                          Map.of(),
                                                          List.of(),
                                                          false,
                                                          Map.of(),
                                                          Map.of()))
                    .build());
        }
        return new McpSchema.ListToolsResult(toolList, null);
    }

    private String buildToolId(String toolName) {
        return AgentUtils.id(CLIENT_NAME, toolName);
    }
}
