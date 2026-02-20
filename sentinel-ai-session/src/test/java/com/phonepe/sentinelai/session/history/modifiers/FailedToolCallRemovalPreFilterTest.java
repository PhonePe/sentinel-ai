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

package com.phonepe.sentinelai.session.history.modifiers;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage.Role;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailedToolCallRemovalPreFilterTest {

    @Test
    void testFilterEmptyListReturnsEmpty() {
        var filter = new FailedToolCallRemovalPreFilter();
        var messages = new ArrayList<AgentMessage>();
        var result = filter.filter(messages);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterKeepsSuccessfulToolCallResponseAndRequest() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-3";
        var runId = "run-3";
        var toolCallId = "tc-success";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, runId, "user message", LocalDateTime.now()),
                                             new ToolCall(sessionId,
                                                          runId,
                                                          "msg-1",
                                                          null,
                                                          toolCallId,
                                                          "testTool",
                                                          "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  toolCallId,
                                                                  "testTool",
                                                                  ErrorType.SUCCESS,
                                                                  "success response",
                                                                  LocalDateTime.now()),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(m -> "msg-1".equals(m.getMessageId())));
        assertTrue(result.stream().anyMatch(m -> "msg-2".equals(m.getMessageId())));
    }

    @Test
    void testFilterMixedSuccessAndFailureToolCalls() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-4";
        var runId = "run-4";
        var successId = "tc-success";
        var failId = "tc-fail";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, runId, "user message", LocalDateTime.now()),
                                             new ToolCall(sessionId,
                                                          runId,
                                                          "msg-1",
                                                          null,
                                                          successId,
                                                          "successTool",
                                                          "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  successId,
                                                                  "successTool",
                                                                  ErrorType.SUCCESS,
                                                                  "ok",
                                                                  LocalDateTime.now()),
                                             new ToolCall(sessionId, runId, "msg-3", null, failId, "failTool", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-4",
                                                                  null,
                                                                  failId,
                                                                  "failTool",
                                                                  ErrorType.TOOL_CALL_TEMPORARY_FAILURE,
                                                                  "temp error",
                                                                  LocalDateTime.now()),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(m -> "msg-1".equals(m.getMessageId())));
        assertTrue(result.stream().anyMatch(m -> "msg-2".equals(m.getMessageId())));
        assertFalse(result.stream().anyMatch(m -> "msg-3".equals(m.getMessageId())));
        assertFalse(result.stream().anyMatch(m -> "msg-4".equals(m.getMessageId())));
    }

    @Test
    void testFilterMultipleRunsInSameSession() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-8";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, "run-1", "user 1", LocalDateTime.now()),
                                             new ToolCall(sessionId, "run-1", "msg-1", null, "tc-1", "tool", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  "run-1",
                                                                  "msg-2",
                                                                  null,
                                                                  "tc-1",
                                                                  "tool",
                                                                  ErrorType.SUCCESS,
                                                                  "ok",
                                                                  LocalDateTime.now()),
                                             new UserPrompt(sessionId, "run-2", "user 2", LocalDateTime.now()),
                                             new ToolCall(sessionId, "run-2", "msg-3", null, "tc-2", "tool", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  "run-2",
                                                                  "msg-4",
                                                                  null,
                                                                  "tc-2",
                                                                  "tool",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "failed",
                                                                  LocalDateTime.now())
        );
        var result = filter.filter(messages);
        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(m -> "run-1".equals(m.getRunId()) && "msg-1".equals(m.getMessageId())));
        assertTrue(result.stream().anyMatch(m -> "run-1".equals(m.getRunId()) && "msg-2".equals(m.getMessageId())));
        assertFalse(result.stream().anyMatch(m -> "run-2".equals(m.getRunId()) && "msg-3".equals(m.getMessageId())));
        assertFalse(result.stream().anyMatch(m -> "run-2".equals(m.getRunId()) && "msg-4".equals(m.getMessageId())));
    }

    @Test
    void testFilterPreservesNonToolCallMessageOrder() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-7";
        var runId = "run-7";
        var userPrompt = new UserPrompt(sessionId, runId, "user message", LocalDateTime.now());
        var textResponse = new Text(sessionId, runId, "response", new ModelUsageStats(), 100);
        var messages = List.<AgentMessage>of(
                                             userPrompt,
                                             new ToolCall(sessionId, runId, "msg-1", null, "tc-1", "tool", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  "tc-1",
                                                                  "tool",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "failed",
                                                                  LocalDateTime.now()),
                                             textResponse
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
        assertEquals(userPrompt, result.get(0));
        assertEquals(textResponse, result.get(1));
    }

    @Test
    void testFilterRemovesFailedToolCallResponseAndRequest() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-2";
        var runId = "run-2";
        var toolCallId = "tc-failed";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, runId, "user message", LocalDateTime.now()),
                                             new ToolCall(sessionId,
                                                          runId,
                                                          "msg-1",
                                                          null,
                                                          toolCallId,
                                                          "testTool",
                                                          "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  toolCallId,
                                                                  "testTool",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "error response",
                                                                  LocalDateTime.now()),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
        assertFalse(result.stream().anyMatch(m -> "msg-1".equals(m.getMessageId())));
        assertFalse(result.stream().anyMatch(m -> "msg-2".equals(m.getMessageId())));
    }

    @Test
    void testFilterWithDifferentFailureTypes() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-6";
        var runId = "run-6";
        var timeoutId = "tc-timeout";
        var permFailId = "tc-perm-fail";
        var tempFailId = "tc-temp-fail";
        var successId = "tc-success";

        var messages = List.<AgentMessage>of(
                                             new ToolCall(sessionId, runId, "msg-1", null, timeoutId, "tool1", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  timeoutId,
                                                                  "tool1",
                                                                  ErrorType.TOOL_CALL_TIMEOUT,
                                                                  "timeout",
                                                                  LocalDateTime.now()),
                                             new ToolCall(sessionId, runId, "msg-3", null, permFailId, "tool2", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-4",
                                                                  null,
                                                                  permFailId,
                                                                  "tool2",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "perm fail",
                                                                  LocalDateTime.now()),
                                             new ToolCall(sessionId, runId, "msg-5", null, tempFailId, "tool3", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-6",
                                                                  null,
                                                                  tempFailId,
                                                                  "tool3",
                                                                  ErrorType.TOOL_CALL_TEMPORARY_FAILURE,
                                                                  "temp fail",
                                                                  LocalDateTime.now()),
                                             new ToolCall(sessionId, runId, "msg-7", null, successId, "tool4", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-8",
                                                                  null,
                                                                  successId,
                                                                  "tool4",
                                                                  ErrorType.SUCCESS,
                                                                  "ok",
                                                                  LocalDateTime.now())
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(m -> "msg-7".equals(m.getMessageId())));
        assertTrue(result.stream().anyMatch(m -> "msg-8".equals(m.getMessageId())));
    }

    @Test
    void testFilterWithGenericTextMessages() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-12";
        var runId = "run-12";
        var messages = List.<AgentMessage>of(
                                             new GenericText(sessionId, runId, Role.USER, "generic text"),
                                             new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(3, result.size());
    }

    @Test
    void testFilterWithMixedMessageTypesAndFailedToolCall() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-14";
        var runId = "run-14";
        var messages = List.<AgentMessage>of(
                                             new SystemPrompt(sessionId, runId, "system", true, "m"),
                                             new GenericText(sessionId, runId, Role.USER, "generic"),
                                             new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                             new ToolCall(sessionId, runId, "msg-1", null, "tc-1", "tool", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  "tc-1",
                                                                  "tool",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "failed",
                                                                  LocalDateTime.now()),
                                             new Text(sessionId, runId, "text", new ModelUsageStats(), 100),
                                             new StructuredOutput(sessionId, runId, "{}", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(5, result.size());
        assertFalse(result.stream().anyMatch(m -> "msg-1".equals(m.getMessageId())));
        assertFalse(result.stream().anyMatch(m -> "msg-2".equals(m.getMessageId())));
    }

    @Test
    void testFilterWithNoToolCallsKeepsAllMessages() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-1";
        var runId = "run-1";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, runId, "user message", LocalDateTime.now()),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
    }

    @Test
    void testFilterWithOnlyFailedToolCallsReturnsEmpty() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-5";
        var runId = "run-5";
        var messages = List.<AgentMessage>of(
                                             new ToolCall(sessionId, runId, "msg-1", null, "tc-1", "tool1", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  "tc-1",
                                                                  "tool1",
                                                                  ErrorType.TOOL_CALL_TIMEOUT,
                                                                  "timeout",
                                                                  LocalDateTime.now()),
                                             new ToolCall(sessionId, runId, "msg-3", null, "tc-2", "tool2", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-4",
                                                                  null,
                                                                  "tc-2",
                                                                  "tool2",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "failed",
                                                                  LocalDateTime.now())
        );
        var result = filter.filter(messages);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterWithStructuredOutputResponse() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-13";
        var runId = "run-13";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                             new StructuredOutput(sessionId,
                                                                  runId,
                                                                  "{\"key\": \"value\"}",
                                                                  new ModelUsageStats(),
                                                                  100),
                                             new ToolCall(sessionId, runId, "msg-1", null, "tc-1", "tool", "{}"),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-2",
                                                                  null,
                                                                  "tc-1",
                                                                  "tool",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "failed",
                                                                  LocalDateTime.now())
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
    }

    @Test
    void testFilterWithSystemPromptMessages() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-11";
        var runId = "run-11";
        var messages = List.<AgentMessage>of(
                                             new SystemPrompt(sessionId, runId, "system prompt", true, "method"),
                                             new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(3, result.size());
    }

    @Test
    void testFilterWithUnmatchedToolCallRequest() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-9";
        var runId = "run-9";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                             new ToolCall(sessionId, runId, "msg-1", null, "tc-orphan", "tool", "{}"),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(3, result.size());
    }

    @Test
    void testFilterWithUnmatchedToolCallResponse() {
        var filter = new FailedToolCallRemovalPreFilter();
        var sessionId = "session-10";
        var runId = "run-10";
        var messages = List.<AgentMessage>of(
                                             new UserPrompt(sessionId, runId, "user", LocalDateTime.now()),
                                             new ToolCallResponse(sessionId,
                                                                  runId,
                                                                  "msg-1",
                                                                  null,
                                                                  "tc-orphan",
                                                                  "tool",
                                                                  ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                  "failed",
                                                                  LocalDateTime.now()),
                                             new Text(sessionId, runId, "response", new ModelUsageStats(), 100)
        );
        var result = filter.filter(messages);
        assertEquals(2, result.size());
        assertFalse(result.stream().anyMatch(m -> "msg-1".equals(m.getMessageId())));
    }
}
