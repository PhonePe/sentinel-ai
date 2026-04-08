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

package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.node.NullNode;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.tools.ExternalTool.ExternalToolResponse;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonContextualDefaultExternalToolRunnerTest {

    private static final String SESSION_ID = "test-session";
    private static final String RUN_ID = "test-run";

    private static final NonContextualDefaultExternalToolRunner RUNNER = new NonContextualDefaultExternalToolRunner(SESSION_ID,
                                                                                                                    RUN_ID,
                                                                                                                    JsonUtils
                                                                                                                            .createMapper());

    private static ExternalTool externalTool(String name, ExternalToolResponse response) {
        return new ExternalTool(toolDefinition(name), NullNode.getInstance(), (ctx, callId, args) -> response);
    }

    private static ToolCall toolCall(String toolCallId, String toolName) {
        return ToolCall.builder()
                .sessionId(SESSION_ID)
                .runId(RUN_ID)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .arguments("{}")
                .build();
    }

    private static ToolDefinition toolDefinition(String name) {
        return ToolDefinition.builder().id(name).name(name).description("test tool").build();
    }

    @Test
    void externalToolCallableExceptionReturnsTemporaryFailure() {
        final var tool = new ExternalTool(toolDefinition("faultyTool"), NullNode.getInstance(), (ctx, callId, args) -> {
            throw new RuntimeException("tool exploded");
        });
        final var tc = toolCall("call-3", "faultyTool");

        final var result = RUNNER.runTool(Map.of("faultyTool", tool), tc);

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-3", result.getToolCallId());
        assertEquals("faultyTool", result.getToolName());
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(RUN_ID, result.getRunId());
        assertTrue(result.getResponse().contains("tool exploded"));
        assertFalse(result.isSuccess());
    }

    @Test
    void externalToolResponseSerializedToJson() {
        final var tool = externalTool("jsonTool", new ExternalToolResponse(Map.of("key", "value"), ErrorType.SUCCESS));
        final var tc = toolCall("call-2", "jsonTool");

        final var result = RUNNER.runTool(Map.of("jsonTool", tool), tc);

        assertEquals(ErrorType.SUCCESS, result.getErrorType());
        assertTrue(result.getResponse().contains("key"));
        assertTrue(result.getResponse().contains("value"));
    }

    @Test
    void externalToolSuccess() {
        final var tool = externalTool("myTool", new ExternalToolResponse("hello", ErrorType.SUCCESS));
        final var tc = toolCall("call-1", "myTool");

        final var result = RUNNER.runTool(Map.of("myTool", tool), tc);

        assertEquals(ErrorType.SUCCESS, result.getErrorType());
        assertEquals("call-1", result.getToolCallId());
        assertEquals("myTool", result.getToolName());
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(RUN_ID, result.getRunId());
        assertTrue(result.isSuccess());
        assertNotNull(result.getSentAt());
    }

    @Test
    void internalToolReturnsPermanentFailure() {
        final var internalTool = new InternalTool(toolDefinition("internalTool"),
                                                  new ToolMethodInfo(List.of(), null, Void.class),
                                                  new Object());
        final var tc = toolCall("call-4", "internalTool");

        final var result = RUNNER.runTool(Map.of("internalTool", internalTool), tc);

        assertEquals(ErrorType.TOOL_CALL_PERMANENT_FAILURE, result.getErrorType());
        assertEquals("call-4", result.getToolCallId());
        assertEquals("internalTool", result.getToolName());
        assertTrue(result.getResponse().contains("not supported"));
        assertFalse(result.isSuccess());
    }

    @Test
    void nullSessionIdFallsBackToRunId() {
        final var runner = new NonContextualDefaultExternalToolRunner(null, RUN_ID, JsonUtils.createMapper());
        final var tool = new ExternalTool(toolDefinition("errTool"), NullNode.getInstance(), (ctx, callId, args) -> {
            throw new RuntimeException("failed");
        });
        final var tc = ToolCall.builder()
                .runId(RUN_ID)
                .toolCallId("call-7")
                .toolName("errTool")
                .arguments("{}")
                .build();

        final var result = runner.runTool(Map.of("errTool", tool), tc);

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-7", result.getToolCallId());
        assertEquals("errTool", result.getToolName());
        assertTrue(result.getResponse().contains("failed"));
    }

    @Test
    void sessionAndRunIdPropagatedToResponse() {
        final var runner = new NonContextualDefaultExternalToolRunner("s-abc", "r-xyz", JsonUtils.createMapper());
        final var tool = externalTool("pingTool", new ExternalToolResponse("pong", ErrorType.SUCCESS));
        final var tc = ToolCall.builder()
                .sessionId("s-abc")
                .runId("r-xyz")
                .toolCallId("call-6")
                .toolName("pingTool")
                .arguments("{}")
                .build();

        final var result = runner.runTool(Map.of("pingTool", tool), tc);

        assertEquals("s-abc", result.getSessionId());
        assertEquals("r-xyz", result.getRunId());
        assertEquals(ErrorType.SUCCESS, result.getErrorType());
    }

    @Test
    void unknownToolThrowsNpe() {
        final var tc = toolCall("call-5", "missingTool");
        final Map<String, ExecutableTool> tools = new HashMap<>();

        assertThrows(NullPointerException.class, () -> RUNNER.runTool(tools, tc));
    }
}
