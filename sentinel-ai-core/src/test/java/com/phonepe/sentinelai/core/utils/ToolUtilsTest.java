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

package com.phonepe.sentinelai.core.utils;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolUtilsTest {

    private static final String SESSION_ID = "session-1";
    private static final String RUN_ID = "run-1";

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
    void processUnhandledExceptionPreservesNullSessionIdWhenProvided() {
        final var toolCall = ToolCall.builder()
                .runId(RUN_ID)
                .toolCallId("call-5")
                .toolName("tool")
                .arguments("{}")
                .build();

        final ToolCallResponse result = ToolUtils.processUnhandledException(null, RUN_ID, toolCall, "err");

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-5", result.getToolCallId());
    }

    @Test
    void processUnhandledExceptionWithExceptionExtractsRootCauseMessage() {
        final var toolCall = toolCall("call-1", "myTool");
        final var cause = new IllegalStateException("root problem");
        final var wrapped = new RuntimeException("wrapper", cause);

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, wrapped);

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-1", result.getToolCallId());
        assertEquals("myTool", result.getToolName());
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(RUN_ID, result.getRunId());
        assertTrue(result.getResponse().contains("root problem"));
        assertFalse(result.isSuccess());
    }

    @Test
    void processUnhandledExceptionWithExceptionUsesExceptionMessageWhenNoCause() {
        final var toolCall = toolCall("call-2", "simpleTool");
        final var exception = new RuntimeException("direct message");

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, exception);

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertTrue(result.getResponse().contains("direct message"));
    }

    @Test
    void processUnhandledExceptionWithStringBuildsExpectedResponse() {
        final var toolCall = toolCall("call-3", "anotherTool");

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, "something failed");

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-3", result.getToolCallId());
        assertEquals("anotherTool", result.getToolName());
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(RUN_ID, result.getRunId());
        assertTrue(result.getResponse().contains("something failed"));
        assertNotNull(result.getSentAt());
        assertFalse(result.isSuccess());
    }

    @Test
    void processUnhandledExceptionWithStringPrefixesErrorRunningTool() {
        final var toolCall = toolCall("call-4", "tool");
        final var errorMessage = "unexpected null";

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, errorMessage);

        assertTrue(result.getResponse().startsWith("Error running tool:"));
        assertTrue(result.getResponse().contains(errorMessage));
    }
}
