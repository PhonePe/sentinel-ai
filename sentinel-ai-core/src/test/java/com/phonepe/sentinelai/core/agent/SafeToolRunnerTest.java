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

package com.phonepe.sentinelai.core.agent;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafeToolRunnerTest {

    private static final String SESSION_ID = "test-session";
    private static final String RUN_ID = "test-run";

    private static ToolCallResponse successResponse(String toolCallId, String toolName, String response) {
        return ToolCallResponse.builder()
                .sessionId(SESSION_ID)
                .runId(RUN_ID)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .response(response)
                .errorType(ErrorType.SUCCESS)
                .build();
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

    @Test
    void customTokenLimitEnforcedWhenExceeded() {
        final var delegateResponse = successResponse("call-6", "tool", "large response");
        final var delegate = (ToolRunner) (tools, tc) -> delegateResponse;
        final var model = mock(Model.class);
        final int contextWindow = 1000;
        final int customPct = 10;       // ceiling = 1000 * 10 / 100 = 100 tokens
        final int exceedingCount = 101; // just over the ceiling
        when(model.estimateTokenCount(anyList(), any(AgentSetup.class))).thenReturn(exceedingCount);

        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .maxToolResponsePercentage(customPct)
                .modelSettings(ModelSettings.builder()
                        .modelAttributes(ModelAttributes.builder()
                                .contextWindowSize(contextWindow)
                                .build())
                        .build())
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var result = runner.runTool(Map.of(), toolCall("call-6", "tool"));

        assertEquals(ErrorType.TOOL_CALL_PERMANENT_FAILURE, result.getErrorType());
        assertTrue(result.getResponse().contains(String.valueOf(100)));       // ceiling = 100
        assertTrue(result.getResponse().contains(String.valueOf(exceedingCount)));
    }

    @Test
    void defaultTokenLimitAppliedWhenMaxTokensIsNegative() {
        final var delegateResponse = successResponse("call-5", "tool", "ok");
        final var delegate = (ToolRunner) (tools, tc) -> delegateResponse;
        final var model = mock(Model.class);
        when(model.estimateTokenCount(anyList(), any(AgentSetup.class))).thenReturn(1);

        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .maxToolResponsePercentage(-5)
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var result = runner.runTool(Map.of(), toolCall("call-5", "tool"));

        assertEquals(ErrorType.SUCCESS, result.getErrorType());
    }

    @Test
    void defaultTokenLimitAppliedWhenMaxTokensIsZero() {
        final var delegateResponse = successResponse("call-4", "tool", "ok");
        final var delegate = (ToolRunner) (tools, tc) -> delegateResponse;
        final var model = mock(Model.class);
        when(model.estimateTokenCount(anyList(), any(AgentSetup.class))).thenReturn(1);

        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .maxToolResponsePercentage(0)
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var result = runner.runTool(Map.of(), toolCall("call-4", "tool"));

        assertEquals(ErrorType.SUCCESS, result.getErrorType());
    }

    @Test
    void delegateExceptionCaughtAndWrapped() {
        final var delegate = (ToolRunner) (tools, tc) -> {
            throw new RuntimeException("something went wrong");
        };
        final var model = mock(Model.class);
        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var result = runner.runTool(Map.of(), toolCall("call-3", "faultyTool"));

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-3", result.getToolCallId());
        assertEquals("faultyTool", result.getToolName());
        assertTrue(result.getResponse().contains("something went wrong"));
        assertFalse(result.isSuccess());
    }

    @Test
    void delegateResponseReturnedWhenWithinTokenLimit() {
        final var delegateResponse = successResponse("call-1", "myTool", "small response");
        final var delegate = (ToolRunner) (tools, tc) -> delegateResponse;
        final var model = mock(Model.class);
        when(model.estimateTokenCount(anyList(), any(AgentSetup.class))).thenReturn(10);

        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var result = runner.runTool(Map.of(), toolCall("call-1", "myTool"));

        assertEquals(ErrorType.SUCCESS, result.getErrorType());
        assertEquals("call-1", result.getToolCallId());
        assertEquals("myTool", result.getToolName());
        assertEquals("small response", result.getResponse());
    }

    @Test
    void responseReplacedWhenTokenLimitExceeded() {
        final var delegateResponse = successResponse("call-2", "bigTool", "very large response content");
        final var delegate = (ToolRunner) (tools, tc) -> delegateResponse;
        final var model = mock(Model.class);
        // Default ceiling: DEFAULT_WINDOW_SIZE(128_000) * DEFAULT_MAX_TOOL_RESPONSE_PERCENTAGE(10) / 100 = 12_800
        final int defaultCeiling = (ModelAttributes.DEFAULT_WINDOW_SIZE
                * AgentSetup.DEFAULT_MAX_TOOL_RESPONSE_PERCENTAGE) / 100;
        final int exceedingTokenCount = defaultCeiling + 1;
        when(model.estimateTokenCount(anyList(), any(AgentSetup.class))).thenReturn(exceedingTokenCount);

        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var result = runner.runTool(Map.of(), toolCall("call-2", "bigTool"));

        assertEquals(ErrorType.TOOL_CALL_PERMANENT_FAILURE, result.getErrorType());
        assertEquals("call-2", result.getToolCallId());
        assertEquals("bigTool", result.getToolName());
        assertTrue(result.getResponse().contains("Tool response too large"));
        assertTrue(result.getResponse().contains(String.valueOf(defaultCeiling)));
        assertTrue(result.getResponse().contains(String.valueOf(exceedingTokenCount)));
        assertFalse(result.isSuccess());
    }

    @Test
    void tokenCountUnknownSkipsGuardAndReturnsResponse() {
        final var delegateResponse = successResponse("call-8", "myTool", "some large response");
        final var delegate = (ToolRunner) (tools, tc) -> delegateResponse;
        final var model = mock(Model.class);
        when(model.estimateTokenCount(anyList(), any(AgentSetup.class))).thenReturn(Model.TOKEN_COUNT_UNKNOWN);

        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var result = runner.runTool(Map.of(), toolCall("call-8", "myTool"));

        // When token count is unknown, the guard is skipped and the original response is returned
        assertEquals(ErrorType.SUCCESS, result.getErrorType());
        assertEquals("call-8", result.getToolCallId());
        assertEquals("myTool", result.getToolName());
        assertEquals("some large response", result.getResponse());
    }

    @Test
    void toolMapAndToolCallPassedToDelegate() {
        final var capturedTools = new AtomicReference<Map<String, ExecutableTool>>();
        final var capturedToolCall = new AtomicReference<ToolCall>();

        final var delegateResponse = successResponse("call-7", "tool", "captured");
        final var delegate = (ToolRunner) (tools, tc) -> {
            capturedTools.set(tools);
            capturedToolCall.set(tc);
            return delegateResponse;
        };
        final var model = mock(Model.class);
        when(model.estimateTokenCount(anyList(), any(AgentSetup.class))).thenReturn(1);

        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .build();

        final var runner = new SafeToolRunner(delegate, setup, model, SESSION_ID, RUN_ID);
        final var tool = mock(ExecutableTool.class);
        final var tools = Map.of("tool", tool);
        final var tc = toolCall("call-7", "tool");

        runner.runTool(tools, tc);

        assertEquals(tools, capturedTools.get());
        assertEquals(tc, capturedToolCall.get());
    }
}
